package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.hibernate.Session
import org.joda.time.DateTime
import org.textup.action.*
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class ContactService implements ManagesDomain.Creater<IndividualPhoneRecordWrapper>, ManagesDomain.Updater<IndividualPhoneRecordWrapper>, ManagesDomain.Deleter {

    MergeActionService mergeActionService
    NotificationActionService notificationActionService
    NumberActionService numberActionService
    ShareActionService shareActionService

    @RollbackOnResultFailure
	Result<IndividualPhoneRecordWrapper> tryCreate(Long pId, TypeMap body) {
        Phones.mustFindActiveForId(pId)
            .then { Phone p1 -> IndividualPhoneRecordWrappers.tryCreate(p1) }
            .then { IndividualPhoneRecordWrapper w1 -> trySetFields(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 -> tryNotifications(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 ->
                numberActionService.tryHandleActions(w1, body)
            }
            .then { IndividualPhoneRecordWrapper w1 -> trySharing(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 -> tryMerge(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 ->
                IOCUtils.resultFactory.success(w1, ResultStatus.CREATED)
            }
	}

    @RollbackOnResultFailure
	Result<IndividualPhoneRecordWrapper> tryUpdate(Long prId, TypeMap body) {
        IndividualPhoneRecordWrappers.mustFindForId(prId)
            .then { IndividualPhoneRecordWrapper w1 -> trySetFields(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 -> tryNotifications(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 ->
                numberActionService.tryHandleActions(w1, body)
            }
            .then { IndividualPhoneRecordWrapper w1 -> trySharing(w1, body) }
            .then { IndividualPhoneRecordWrapper w1 -> tryMerge(w1, body) }
	}

    @RollbackOnResultFailure
    Result<Void> tryDelete(Long prId) {
        IndividualPhoneRecordWrappers.mustFindForId(prId)
            .then { IndividualPhoneRecordWrapper w1 -> w1.tryDelete().curry(w1) }
            .then { IndividualPhoneRecordWrapper w1 -> DomainUtils.trySave(w1) }
            .then { Result.void() }
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

        if (notificationActionService.hasActions(body)) {
            w1.tryGetMutablePhone() // NOT original phone, notification settings on shared phones too
                .then { Phone p1 -> w1.tryGetRecord().curry(p1) }
                .then { Phone p1, Record rec1 ->
                    notificationActionService.tryHandleActions(Tuple.create(p1, rec1.id), body)
                }
                .then { DomainUtils.trySave(w1) }
        }
        else { DomainUtils.trySave(w1) }
    }

    protected Result<IndividualPhoneRecordWrapper> trySharing(IndividualPhoneRecordWrapper w1,
        TypeMap body) {

        if (shareActionService.hasActions(body)) {
            w1.tryUnwrap()
                .then { IndividualPhoneRecord ipr1 -> shareActionService.tryHandleActions(ipr1, body) }
                .then { DomainUtils.trySave(w1) }
        }
        else { DomainUtils.trySave(w1) }
    }

    protected Result<IndividualPhoneRecordWrapper> tryMerge(IndividualPhoneRecordWrapper w1,
        TypeMap body) {

        if (mergeActionService.hasActions(body)) {
            w1.tryUnwrap()
                .then { IndividualPhoneRecord ipr1 ->
                    mergeActionService.tryHandleActions(ipr1.id, body)
                }
                .then { DomainUtils.trySave(w1) }
        }
        else { DomainUtils.trySave(w1) }
    }
}
