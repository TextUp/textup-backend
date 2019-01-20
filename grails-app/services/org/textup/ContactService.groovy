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

    MergeActionService mergeActionService
    NotificationActionService notificationActionService
    NumberActionsService numberActionsService
    ShareActionService shareActionService

    @RollbackOnResultFailure
	Result<IndividualPhoneRecordWrapper> create(Long ownerId, PhoneOwnershipType type, TypeMap body) {
        Phones.mustFindActiveForOwner(ownerId, type)
            .then { Phone p1 -> IndividualPhoneRecordWrappers.tryCreate(p1) }
            .then { IndividualPhoneRecordWrapper w1 -> trySetFields(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 -> tryNotifications(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 ->
                numberActionsService.tryHandleActions(w1, body)
            }
            .then { IndividualPhoneRecordWrapper w1 -> trySharing(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 -> tryMerge(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 ->
                IOCUtils.resultFactory.success(w1, ResultStatus.CREATED)
            }
	}

    @RollbackOnResultFailure
	Result<IndividualPhoneRecordWrapper> update(Long iprId, TypeMap body) {
        IndividualPhoneRecordWrappers.mustFindForId(iprId)
            .then { IndividualPhoneRecordWrapper w1 -> trySetFields(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 -> tryNotifications(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 ->
                numberActionsService.tryHandleActions(w1, body)
            }
            .then { IndividualPhoneRecordWrapper w1 -> trySharing(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 -> tryMerge(w1, body) }
	}

    @RollbackOnResultFailure
    Result<Void> delete(Long iprId) {
        IndividualPhoneRecordWrappers.mustFindForId(iprId)
            .then { IndividualPhoneRecordWrapper w1 -> w1.tryDelete() }
            .then { DomainUtils.trySave(w1) }
            .then { IOCUtils.resultFactory.success() }
    }

    // Helpers
    // -------

    protected Result<IndividualPhoneRecordWrapper> trySetFields(IndividualPhoneRecordWrapper w1,
        TypeMap body) {

        w1.trySetNameIfPresent(body.string("name"))
            .then { w1.trySetNoteIfPresent(body.string("note")) }
            .then { w1.trySetLanguageIfPresent(body.enum(VoiceLanguage, "language")) }
            .then { w1.trySetStatusIfPresent(body.enum(PhoneRecordStatus, "status")) }
            .then { DomainUtils.trySave(w1) }
    }

    protected Result<IndividualPhoneRecordWrapper> tryNotifications(IndividualPhoneRecordWrapper w1,
        TypeMap body) {

        w1.tryGetPhone()
            .then { Phone p1 -> w1.tryGetRecord().curry(p1) }
            .then { Phone p1, Record rec1 ->
                notificationActionService.tryHandleActions(Tuple.create(p1, rec1.id), body)
            }
            .then { DomainUtils.trySave(w1) }
    }

    protected Result<IndividualPhoneRecordWrapper> trySharing(IndividualPhoneRecordWrapper w1,
        TypeMap body) {

        w1.tryUnwrap()
            .then { IndividualPhoneRecord ipr1 -> shareActionService.tryHandleActions(ipr1, body) }
            .then { DomainUtils.trySave(w1) }
    }


    protected Result<IndividualPhoneRecordWrapper> tryMerge(IndividualPhoneRecordWrapper w1,
        TypeMap body) {

        w1.tryUnwrap()
            .then { IndividualPhoneRecord ipr1 ->
                mergeActionService.tryHandleActions(ipr1.id, body)
            }
            .then { DomainUtils.trySave(w1) }
    }
}
