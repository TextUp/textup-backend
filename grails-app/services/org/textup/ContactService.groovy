package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.hibernate.Session
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.action.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class ContactService {

    FutureMessageJobService futureMessageJobService
    MergeService mergeService
    NotificationSettingsService notificationSettingsService
    NumberService numberService
    SharedContactService sharedContactService
    SocketService socketService

    @RollbackOnResultFailure
	Result<Contact> create(Long ownerId, PhoneOwnershipType type, Map body) {
        Phone.findByOwner(ownerId, type)
            .then { Phone p1 -> Contact.create(p1) }
            .then { Contact c1 -> handleNotificationActions(c1, body).curry(c1) } // delegate ok
            .then { Contact c1 -> handleNumberActions(c1, body).curry(c1) } // delegate ok
            .then { Contact c1 -> handleShareActions(c1, body).curry(c1) } // only owner
            .then { Contact c1 -> handleMergeActions(c1, body).curry(c1) } // only owner
            .then { Contact c1 -> updateContactInfo(c1, body) } // all collaborators
            .then { Contact c1 -> IOCUtils.resultFactory.success(c1, ResultStatus.CREATED) }
	}

    @RollbackOnResultFailure
	Result<Contactable> update(Long cId, Map body, Long scId = null) {
        Contact c1 = Contact.get(cId)
        SharedContact sc1 = scId ? SharedContact.get(scId) : null
        // only mandate existence for the shared contact if a shared contact id is provided
        if (!c1 || (scId && !sc1)) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("contactService.update.notFound",
                ResultStatus.NOT_FOUND, [cId])
        }
        handleNotificationActions(c1, body, sc1) // delegate ok
            .then { handleNumberActions(c1, body, sc1) } // delegate ok
            .then { handleShareActions(c1, body, sc1) } // only owner
            .then { handleMergeActions(c1, body, sc1) } // only owner
            .then { updateContactInfo(c1, body, sc1) } // all collaborators
            // if passing in a shared contact to update, we want to return the shared contact
            // to the json marshaller so we don't accidentally return the original contact's status
            // when we mean to return the shared contact's status, for example
            .then { IOCUtils.resultFactory.success(sc1 ?: c1) }
	}

    @RollbackOnResultFailure
    Result<Void> delete(Long cId) {
        Contact c1 = Contact.get(cId)
        if (c1) {
            c1.isDeleted = true
            if (c1.save()) {
                Collection<FutureMessage> fMsgs = c1.record.getFutureMessages()
                ResultGroup<?> resGroup = futureMessageJobService.cancelAll(fMsgs)
                if (resGroup.anyFailures) {
                    IOCUtils.resultFactory.failWithGroup(resGroup)
                }
                else { IOCUtils.resultFactory.success() }
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(c1.errors) }
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("contactService.delete.notFound",
                ResultStatus.NOT_FOUND, [cId])
        }
    }

    // Helpers
    // -------

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
                if (!sc1.save()) {
                    return IOCUtils.resultFactory.failWithValidationErrors(sc1.errors)
                }
            }
            else {
                c1.status = newStat1
                c1.lastTouched = DateTime.now()
            }
        }
        if (c1.save()) {
            IOCUtils.resultFactory.success(c1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(c1.errors) }
    }

    protected Result<Void> handleNotificationActions(Contact c1, Map body, SharedContact sc1 = null) {
        // collaborators with delegate permissions should be able to modify notification settings
        // because they also receive notifications and also need the ability to have fine-grained
        // notification settings just as the original contact owner.
        if (body.doNotificationActions  && (!sc1 || sc1.canModify)) {
            notificationSettingsService.handleActions(c1.phone, c1.record.id, body.doNotificationActions)
        }
        else { IOCUtils.resultFactory.success() }
    }

    protected Result<Void> handleNumberActions(Contact c1, Map body, SharedContact sc1 = null) {
        //do at the beginning so we don't need to discard any field changes
        //number actions validate only, see below for number actions
        if (body.doNumberActions && (!sc1 || sc1.canModify)) {
            numberService.handleActions(c1, body)
        }
        else { IOCUtils.resultFactory.success() }
    }

    protected Result<Void> handleShareActions(Contact c1, Map body, SharedContact sc1 = null) {
        if (body.doShareActions && !sc1) {
            sharedContactService.handleActions(c1, body)
        }
        else { IOCUtils.resultFactory.success() }
    }


    protected Result<Void> handleMergeActions(Contact c1, Map body, SharedContact sc1 = null) {
        if (body.doMergeActions && !sc1) {
            mergeService.handleActions(c1, body)
        }
        else { IOCUtils.resultFactory.success() }
    }
}
