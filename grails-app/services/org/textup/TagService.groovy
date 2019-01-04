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
    protected Result<ContactTag> doTagActions(ContactTag ct1, Map body) {
        if (body.doTagActions) {
            ActionContainer ac1 = new ActionContainer<>(ContactTagAction, body.doTagActions)
            if (!ac1.validate()) {
                return IOCUtils.resultFactory.failWithValidationErrors(ac1.errors)
            }
            ResultGroup<?> resGroup = new ResultGroup<>()
            ac1.actions.each { ContactTagAction a1 ->
                Contact c1 = a1.contact
                if (ct1.phone?.id != c1.phone?.id) {
                    resGroup << IOCUtils.resultFactory.failWithCodeAndStatus(
                        "tagService.update.contactForbidden", ResultStatus.FORBIDDEN, [a1.id])
                }
                switch (a1) {
                    case Constants.TAG_ACTION_ADD:
                        ct1.addToMembers(c1)
                        break
                    default: // Constants.TAG_ACTION_REMOVE
                        ct1.removeFromMembers(c1)
                }
            }
            if (resGroup.anyFailures) {
                return IOCUtils.resultFactory.failWithGroup(resGroup)
            }
        }
        IOCUtils.resultFactory.success(ct1)
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
                ResultGroup<?> resGroup = futureMessageJobService.cancelAll(fMsgs)
                if (resGroup.anyFailures) {
                    IOCUtils.resultFactory.failWithGroup(resGroup)
                }
                else { IOCUtils.resultFactory.success() }
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(t1.errors) }
    	}
    	else {
    		IOCUtils.resultFactory.failWithCodeAndStatus("tagService.delete.notFound",
                ResultStatus.NOT_FOUND, [tId])
    	}
    }
}
