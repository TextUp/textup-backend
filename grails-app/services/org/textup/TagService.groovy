package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class TagService {

    AuthService authService
    FutureMessageJobService futureMessageJobService
    NotificationSettingsService notificationSettingsService

    // Create
    // ------

    Result<ContactTag> createForTeam(Long tId, Map body) {
        create(Team.get(tId)?.phone, body)
    }
    Result<ContactTag> createForStaff(Map body) {
        create(authService.loggedInAndActive?.phone, body)
    }

    @RollbackOnResultFailure
    protected Result<ContactTag> create(Phone p1, Map body) {
        if (p1) {
            // // TODO fix
            // p1.createTag(body)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("tagService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }

    // Update
    // ------

    @RollbackOnResultFailure
    Result<ContactTag> update(Long tId, Map body) {
        findTagFromId(tId)
            .then { ContactTag ct1 -> handleNotificationActions(ct1, body).curry(ct1) }
            .then { ContactTag ct1 -> doTagActions(ct1, body) }
            .then { ContactTag ct1 -> updateTagInfo(ct1, body) }
    }
    protected Result<ContactTag> findTagFromId(Long ctId) {
        ContactTag ct1 = ContactTag.get(ctId)
        if (ct1) {
            IOCUtils.resultFactory.success(ct1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("tagService.update.notFound",
                ResultStatus.NOT_FOUND, [ctId])
        }
    }
    protected Result<ContactTag> updateTagInfo(ContactTag ct1, Map body) {
        ct1.with {
            if (body.name) name = body.name
            if (body.hexColor) hexColor = body.hexColor
            if (body.language) language = TypeConversionUtils.convertEnum(VoiceLanguage, body.language)
        }
        if (ct1.save()) {
            IOCUtils.resultFactory.success(ct1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(ct1.errors) }
    }
    protected Result<Void> handleNotificationActions(ContactTag ct1, Map body) {
        if (body.doNotificationActions) {
            notificationSettingsService.handleActions(ct1.phone, ct1.record.id, body.doNotificationActions)
        }
        else { IOCUtils.resultFactory.success() }
    }


    // Delete
    // ------

    @RollbackOnResultFailure
    Result<Void> delete(Long tId) {
		ContactTag t1 = ContactTag.get(tId)
    	if (t1) {
			t1.isDeleted = true
            if (t1.save()) {
                Collection<FutureMessage> fMsgs = t1.record.getFutureMessages()
                futureMessageJobService
                    .cancelAll(fMsgs)
                    .toResult()
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(t1.errors) }
    	}
    	else {
    		IOCUtils.resultFactory.failWithCodeAndStatus("tagService.delete.notFound",
                ResultStatus.NOT_FOUND, [tId])
    	}
    }
}
