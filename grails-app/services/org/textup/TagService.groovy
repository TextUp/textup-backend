package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
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
class TagService implements ManagesDomain.Creater<GroupPhoneRecord>, ManagesDomain.Updater<GroupPhoneRecord>, ManagesDomain.Deleter {

    NotificationActionService notificationActionService
    TagActionService tagActionService

    @RollbackOnResultFailure
    Result<GroupPhoneRecord> create(Long pId, TypeMap body) {
        Phones.mustFindActiveForId(pId)
            .then { Phone p1 -> GroupPhoneRecord.tryCreate(p1, body.string("name")) }
            .then { GroupPhoneRecord gpr1 -> trySetFields(gpr1, body) }
            .then { GroupPhoneRecord gpr1 -> tryNotifications(gpr1, body) }
            .then { GroupPhoneRecord gpr1 -> tagActionService.tryHandleActions(gpr1, body) }
            .then { Phone p1 -> DomainUtils.trySave(gpr1, ResultStatus.CREATED) }
    }

    @RollbackOnResultFailure
    Result<GroupPhoneRecord> update(Long grpId, TypeMap body) {
        GroupPhoneRecords.mustFindForId(gprId)
            .then { GroupPhoneRecord gpr1 -> trySetFields(gpr1, body) }
            .then { GroupPhoneRecord gpr1 -> tryNotifications(gpr1, body) }
            .then { GroupPhoneRecord gpr1 -> tagActionService.tryHandleActions(gpr1, body) }
    }

    @RollbackOnResultFailure
    Result<Void> delete(Long tId) {
        GroupPhoneRecords.mustFindForId(gprId)
            .then { GroupPhoneRecord gpr1 ->
                gpr1.isDeleted = true
                gpr1.tryCancelFutureMessages()
            }
            .then { DomainUtils.trySave(gpr1) }
            .then { Result.void() }
    }

    // Helpers
    // -------

    protected Result<GroupPhoneRecord> trySetFields(GroupPhoneRecord gpr1, TypeMap body) {
        gpr1.with {
            if (body.name) name = body.name
            if (body.hexColor) hexColor = body.hexColor
            if (body.language) language = body.enum(VoiceLanguage, "language")
        }
        DomainUtils.trySave(gpr1)
    }

    protected Result<GroupPhoneRecord> tryNotifications(GroupPhoneRecord gpr1, TypeMap body) {
        notificationActionService.tryHandleActions(Tuple.create(gpr1.phone, gpr1.record.id), body)
            .then { DomainUtils.trySave(gpr1) }
    }
}
