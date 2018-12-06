package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.hibernate.Session
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.action.*
import org.textup.validator.Author

@GrailsTypeChecked
@Transactional
class ContactService {

    AuthService authService
    DuplicateService duplicateService
    FutureMessageJobService futureMessageJobService
    NotificationService notificationService
    ResultFactory resultFactory
    SocketService socketService

    // Create
    // ------

    Result<Contact> createForTeam(Long tId, Map body) {
        create(Team.get(tId)?.phone, body)
    }
    Result<Contact> createForStaff(Map body) {
        create(authService.loggedInAndActive?.phone, body)
    }

    @RollbackOnResultFailure
	protected Result<Contact> create(Phone p1, Map body) {
    	if (!p1) {
            return resultFactory.failWithCodeAndStatus("contactService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
		List<String> nums = []
		if (body.doNumberActions) {
            ActionContainer ac1 = new ActionContainer(body.doNumberActions)
            List<ContactNumberAction> actions = ac1.validateAndBuildActions(ContactNumberAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
            actions
                .sort { ContactNumberAction a1 -> a1.preference }
                .each { ContactNumberAction a1 ->
                    if (a1.matches(Constants.NUMBER_ACTION_MERGE)) { nums << a1.number }
                }
		}
        createHelper(p1, body, nums)
	}
    // factored out for mocking during testing
    protected Result<Contact> createHelper(Phone p1, Map body, List<String> nums) {
        p1.createContact(body, nums)
    }

    // Update
    // ------

    @RollbackOnResultFailure
	Result<Contactable> update(Long cId, Map body, Long scId = null) {
        Contact c1 = Contact.get(cId)
        SharedContact sc1
        if (scId) { sc1 = SharedContact.get(scId) }
        // only mandate existence for the shared contact if a shared contact id is provided
        if (!c1 || (scId && !sc1)) {
            return resultFactory.failWithCodeAndStatus("contactService.update.notFound",
                ResultStatus.NOT_FOUND, [cId])
        }
        handleNotificationActions(c1, body, sc1) // delegate ok
            .then({ Contact cont1 -> handleNumberActions(cont1, body, sc1) }) // delegate ok
            .then({ Contact cont1 -> handleShareActions(cont1, body, sc1) }) // no for collaborators
            .then({ Contact cont1 -> handleMergeActions(cont1, body, sc1) }) // no for collaborators
            .then({ Contact cont1 -> updateContactInfo(cont1, body, sc1) }) // all collaborators
            // if passing in a shared contact to update, we want to return the shared contact
            // to the json marshaller so we don't accidentally return the original contact's status
            // when we mean to return the shared contact's status, for example
            .then({ Contact cont1 -> resultFactory.success(sc1 ?: cont1) })
	}
    protected Result<Contact> updateContactInfo(Contact c1, Map body, SharedContact sc1 = null) {
        if (!sc1 || sc1.canModify) {
            //update other fields only if my contact or with modify permissions
            c1.with {
                if (body.name) name = body.name
                if (body.note) note = body.note
            }
            // since we are updating an existing contact, we can be sure that this contact's
            // record has already been initialized
            if (body.language) {
                c1.record.language = TypeConversionUtils.convertEnum(VoiceLanguage, body.language)
                if (!c1.record.save()) {
                    return resultFactory.failWithValidationErrors(c1.record.errors)
                }
            }
        }
        // both owner of the contact and active collaborators of all permissions can modify status
        // if updating the status, update on the shared contact if available to avoid overwriting
        // the status on the original contact
        if (body.status) {
            ContactStatus newStat1 = TypeConversionUtils.convertEnum(ContactStatus, body.status)
            if (sc1) {
                sc1.status = newStat1
                sc1.lastTouched = DateTime.now()
                if (!sc1.save()) { return resultFactory.failWithValidationErrors(sc1.errors) }
            }
            else {
                c1.status = newStat1
                c1.lastTouched = DateTime.now()
            }
        }
        if (c1.save()) {
            resultFactory.success(c1)
        }
        else { resultFactory.failWithValidationErrors(c1.errors) }
    }
    protected Result<Contact> handleNotificationActions(Contact c1, Map body, SharedContact sc1 = null) {
        // collaborators with delegate permissions should be able to modify notification settings
        // because they also receive notifications and also need the ability to have fine-grained
        // notification settings just as the original contact owner.
        if (body.doNotificationActions  && (!sc1 || sc1.canModify)) {
            Result<Void> res = notificationService.handleNotificationActions(c1.phone,
                c1.record.id, body.doNotificationActions)
            if (!res.success) {
                return resultFactory.failWithResultsAndStatus([res], res.status)
            }
        }
        resultFactory.success(c1)
    }
    protected Result<Contact> handleNumberActions(Contact c1, Map body, SharedContact sc1 = null) {
        //do at the beginning so we don't need to discard any field changes
        //number actions validate only, see below for number actions
        if (body.doNumberActions && (!sc1 || sc1.canModify)) {
            ActionContainer ac1 = new ActionContainer(body.doNumberActions)
            List<ContactNumberAction> actions = ac1.validateAndBuildActions(ContactNumberAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
            Collection<Result<Contact>> failResults = []
            for (ContactNumberAction a1 in actions) {
                Result<?> res
                switch (a1) {
                    case Constants.NUMBER_ACTION_MERGE:
                        res = c1.mergeNumber(a1.number, [preference:a1.preference])
                        break
                    default: // Constants.NUMBER_ACTION_DELETE
                        res = c1.deleteNumber(a1.number)
                }
                if (!res.success) {
                    failResults << resultFactory.failWithResultsAndStatus([res], res.status)
                }
            }
            if (failResults) {
                return resultFactory.failWithResultsAndStatus(failResults,
                    ResultStatus.UNPROCESSABLE_ENTITY)
            }
        }
        resultFactory.success(c1)
    }
    protected Result<Contact> handleShareActions(Contact c1, Map body, SharedContact sc1 = null) {
        if (body.doShareActions && !sc1) {
            ActionContainer ac1 = new ActionContainer(body.doShareActions)
            List<ShareContactAction> actions = ac1.validateAndBuildActions(ShareContactAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
            Map<Phone,SharePermission> sharedWithToPermission = new HashMap<>()
            Collection<Phone> stopSharingPhones = []
            Collection<Result<Contact>> failResults = []
            for (ShareContactAction a1 in actions) {
                Result<?> res
                Phone p1 = a1.phone
                switch (a1) {
                    case Constants.SHARE_ACTION_MERGE:
                        res = c1.phone.share(c1, p1, a1.permissionAsEnum)
                        sharedWithToPermission[p1] = a1.permissionAsEnum
                        break
                    default: // Constants.SHARE_ACTION_STOP
                        res = c1.phone.stopShare(c1, p1)
                        stopSharingPhones << p1
                }
                if (!res.success) {
                    failResults << resultFactory.failWithResultsAndStatus([res], res.status)
                }
            }
            if (failResults) {
                return resultFactory.failWithResultsAndStatus(failResults,
                    ResultStatus.UNPROCESSABLE_ENTITY)
            }
            if (sharedWithToPermission || stopSharingPhones) {
                recordSharingChanges(c1.record, sharedWithToPermission, stopSharingPhones)
                    .logFail("ContactService.handleShareActions: record sharing changes")
            }
        }
        resultFactory.success(c1)
    }
    protected ResultGroup<RecordNote> recordSharingChanges(Record rec,
        Map<Phone,SharePermission> sharedWithToPermission, Collection<Phone> stopSharingPhones) {

        HashSet<String> stopNames = new HashSet<>(),
            viewNames = new HashSet<>(),
            delegateNames = new HashSet<>()
        stopSharingPhones.each { Phone p1 -> stopNames.addAll(p1.owner.buildAllStaff()*.name) }
        sharedWithToPermission.each { Phone p1, SharePermission perm1 ->
            HashSet<String> namesSet = (perm1 == SharePermission.DELEGATE) ? delegateNames : viewNames
            namesSet.addAll(p1.owner.buildAllStaff()*.name)
        }
        Author auth = authService.loggedInAndActive.toAuthor()
        ResultGroup<RecordNote> resGroup = new ResultGroup<>()
        if (stopNames) {
            resGroup.add(recordSharingChangesHelper(rec, stopNames, "note.sharing.stop", auth))
        }
        if (viewNames) {
            resGroup.add(recordSharingChangesHelper(rec, viewNames, "note.sharing.view", auth))
        }
        if (delegateNames) {
            resGroup.add(recordSharingChangesHelper(rec, delegateNames, "note.sharing.delegate", auth))
        }
        // push new system notes to the app
        socketService.sendItems(resGroup.payload)
            .logFail("ContactService.recordSharingChanges: sending items through socket")
        resGroup
    }
    protected Result<RecordNote> recordSharingChangesHelper(Record rec, HashSet<String> names,
        String code, Author auth) {
        List<String> namesList = new ArrayList<>(names)
        String namesString = CollectionUtils.joinWithDifferentLast(namesList, ", ", ", and "),
            contents = IOCUtils.getMessage(code, [namesString])
        RecordNote rNote1 = new RecordNote(record:rec, isReadOnly:true, noteContents:contents)
        rNote1.author = auth
        if (rNote1.save()) {
            resultFactory.success(rNote1)
        }
        else { resultFactory.failWithValidationErrors(rNote1.errors) }
    }
    protected Result<Contact> handleMergeActions(Contact c1, Map body, SharedContact sc1 = null) {
        if (body.doMergeActions && !sc1) {
            ActionContainer ac1 = new ActionContainer(body.doMergeActions)
            List<MergeAction> actions = ac1.validateAndBuildActions(MergeAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
            Collection<Result<Contact>> failResults = []
            for (MergeAction a1 in actions) {
                String name, note
                switch (a1) {
                    case Constants.MERGE_ACTION_DEFAULT:
                        name = c1.name; note = c1.note; break;
                    default: // Constants.MERGE_ACTION_RECONCILE
                        name = a1.name; note = a1.note;
                }
                Result<Contact> res = duplicateService.merge(c1, a1.contacts)
                if (res.success) {
                    c1.name = name
                    c1.note = note
                    // mark merged-in contacts as deleted instead of outright deleting these contacts
                    // During testing, found that the interwoven cascading relationship were very unreliable,
                    // sometimes throwing a cascade error and sometimes not. Therefore, to avoid this potential
                    // unreliability, we set a deleted flag and effectively exclude this contact as deleted
                    Collection<Result<Contact>> failedMergeSaves = []
                    for (Contact mergeContact in a1.contacts) {
                        mergeContact.isDeleted = true
                        if (!mergeContact.save()) {
                            failedMergeSaves << resultFactory.failWithValidationErrors(mergeContact.errors)
                        }
                    }
                    if (failedMergeSaves) {
                        return resultFactory.failWithResultsAndStatus(failedMergeSaves,
                            ResultStatus.UNPROCESSABLE_ENTITY)
                    }
                }
                else { failResults << resultFactory.failWithResultsAndStatus([res], res.status) }
            }
            if (failResults) {
                return resultFactory.failWithResultsAndStatus(failResults,
                    ResultStatus.UNPROCESSABLE_ENTITY)
            }
        }
        resultFactory.success(c1)
    }

    // Delete
    // ------

    @RollbackOnResultFailure
    Result<Void> delete(Long cId) {
        Contact c1 = Contact.get(cId)

        if (c1) {
            c1.isDeleted = true
            if (c1.save()) {
                Collection<FutureMessage> fMsgs = c1.record.getFutureMessages()
                ResultGroup<?> resGroup = futureMessageJobService.cancelAll(fMsgs)
                if (resGroup.anyFailures) {
                    resultFactory.failWithGroup(resGroup)
                }
                else { resultFactory.success() }
            }
            else { resultFactory.failWithValidationErrors(c1.errors) }
        }
        else {
            resultFactory.failWithCodeAndStatus("contactService.delete.notFound",
                ResultStatus.NOT_FOUND, [cId])
        }
    }
}
