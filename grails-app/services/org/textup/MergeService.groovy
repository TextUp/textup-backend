package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.springframework.transaction.annotation.Propagation
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.action.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class MergeService {

    @RollbackOnResultFailure
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    Result<Void> handleActions(Contact c1, Map body) {
        ActionContainer ac1 = new ActionContainer<>(MergeAction, body.doMergeActions)
        if (!ac1.validate()) {
            return IOCUtils.resultFactory.failWithValidationErrors(ac1.errors)
        }
        ResultGroup<Contact> resGroup = new ResultGroup<>()
        ac1.actions.each { MergeAction a1 ->
            String name, note
            switch (a1) {
                case Constants.MERGE_ACTION_DEFAULT:
                    resGroup << mergeContacts(c1, a1.contacts)
                    break
                default: // Constants.MERGE_ACTION_RECONCILE
                    resGroup << mergeContacts(c1, a1.contacts, a1.name, a1.note)
            }
        }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success() }
    }

    protected Result<Contact> mergeContacts(Contact targetContact, Collection<Contact> contactsToMerge,
        String newName, String newNote) {

        mergeContacts(targetContact, contactsToMerge, newName, newNote).then { Contact c1 ->
            c1.name = newName
            c1.note = newNote
            IOCUtils.resultFactory.success(c1)
        }
    }

    // (1) We made the merge method require a new transaction in order to insulate it from
    // potential changes made to the merged-in contacts. Changes to the merged-in contacts
    // will trigger a cascade on save that will undo the merge operations. For example, if we
    // set the isDeleted flag to true for the merged-in contacts within this method, then the
    // tags that contact the merged-in contacts will continue to have these as their members
    // because their membership we re-saved in the save cascade.
    // (2) Requiring a new transaction for this method only works when this method is called
    // from another service. Self-calls by another method in DuplicateService will not trigger
    // the interceptor and this merge method will execute in the same transaction. This is the
    // behavior of the underlying Spring Transaction abstraction. See
    // http://www.tothenew.com/blog/grails-transactions-using-transactional-annotations-and-propagation-requires_new/
    protected Result<Contact> mergeContacts(Contact targetContact, Collection<Contact> contactsToMerge) {
        if (!contactsToMerge) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("duplicateService.merge.missingMergeContacts",
                ResultStatus.BAD_REQUEST)
        }
        // BECAUSE OF THE PROPAGATION REQUIRES NEW, all of the contacts passed in as parameters
        // are detached from the session since opening a new transaction also starts a new session
        // We don't make any modifications on the merged-in contacts so it is all right if they
        // are detached. However, we do modify the targetContact so we must manually re-fetch it
        // before we can proceed. We can't just reattach it because we can't have one object
        // attached simultaneously to two open sessions
        if (!targetContact.isAttached()) {
            targetContact = Contact.get(targetContact.id)
        }
        // find the tags we are interested BEFORE we delete mark the contact we are merging in
        // as deleted because this finder will ignore deleted contacts
        Collection<ContactTag> tagsToMerge = ContactTag.findEveryByContactIds(contactsToMerge*.id)
        // collate items
        Collection<ContactStatus> statuses = [targetContact.status]
        Collection<Record> toMergeRecords = []
        Collection<ContactNumber> mergeNums = []
        for (Contact mergeContact in contactsToMerge) {
            mergeNums += mergeContact.numbers
            toMergeRecords << mergeContact.record
            statuses << mergeContact.status
        }
        // transfer appropriate associations
        targetContact.status = findMostPermissibleContactStatus(statuses)
        Result<Void> res =

        mergeContactTags(targetContact, tagsToMerge, contactsToMerge)
            .then { mergeSharedContacts(targetContact, contactsToMerge) }
            .then { mergeRecords(targetContact.record, toMergeRecords) }
            .then { mergeContactNumbers(targetContact, mergeNums) }
            .then { deleteMergedContacts(contactsToMerge) }
            .then {
                if (targetContact.save()) {
                    IOCUtils.resultFactory.success(targetContact)
                }
                else { IOCUtils.resultFactory.failWithValidationErrors(targetContact.errors) }
            }
    }

    protected ContactStatus findMostPermissibleContactStatus(Collection<ContactStatus> statuses) {
        if ([ContactStatus.UNREAD, ContactStatus.ACTIVE].any { ContactStatus s -> s in statuses }) {
            ContactStatus.ACTIVE
        }
        else if (ContactStatus.ARCHIVED in statuses) {
            ContactStatus.ARCHIVED
        }
        else { ContactStatus.BLOCKED }
    }

    protected Result<Void> mergeContactNumbers(Contact targetContact, Collection<ContactNumber> mergeNums) {
        ResultGroup<?> resGroup = new ResultGroup<>()
        mergeNums.each { ContactNumber num ->
            resGroup << targetContact.mergeNumber(num, num.preference)
        }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success() }
    }

    protected Result<Void> mergeContactTags(Contact targetContact, Collection<ContactTag> mergeContactTags,
        Collection<Contact> mergeContacts) {
        try {
            HashSet<Long> mergeIds = new HashSet<>(mergeContacts*.id)
            mergeContactTags
                .each { ContactTag tag1 ->
                    Collection<Contact> contactsToRemove = tag1.members.findAll { Contact c1 -> c1.id in mergeIds }
                    contactsToRemove.each { Contact c1 ->
                        tag1.removeFromMembers(c1)
                    }
                    tag1.addToMembers(targetContact)
                    tag1.save()
                }
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) {
            log.error("DuplicateService.mergeTags: ${e.message}")
            e.printStackTrace()
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }

    protected Result<Void> mergeSharedContacts(Contact targetContact, Collection<Contact> mergeContacts) {
        try {
            SharedContacts
                .forContactsNoJoins(mergeContacts)
                .updateAll(contact: targetContact)
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) {
            log.error("DuplicateService.mergeSharedContacts: ${e.message}")
            e.printStackTrace()
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }

    protected Result<Void> mergeRecords(Record targetRecord, Collection<Record> toMergeRecords) {
        try {
            RecordItem
                .forRecords(toMergeRecords)
                .updateAll(record: targetRecord)
            FutureMessage
                .forRecords(toMergeRecords)
                .updateAll(record: targetRecord)
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) {
            log.error("DuplicateService.mergeRecords: ${e.message}")
            e.printStackTrace()
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }

    protected Result<Void> deleteMergedContacts(Collection<Contact> contactsToMerge) {
        ResultGroup<?> resGroup = new ResultGroup<>()
        contactsToMerge.each { Contact mc1 ->
            mc1.isDeleted = true
            if (!mc1.save()) {
                resGroup << IOCUtils.resultFactory.failWithValidationErrors(mc1.errors)
            }
        }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success() }
    }
}
