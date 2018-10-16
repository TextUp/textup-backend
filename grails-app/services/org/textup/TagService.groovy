package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.hibernate.Session
import org.textup.type.VoiceLanguage
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.action.ActionContainer
import org.textup.validator.action.ContactTagAction

@GrailsCompileStatic
@Transactional
class TagService {

    ResultFactory resultFactory
    AuthService authService
    FutureMessageJobService futureMessageJobService
    NotificationService notificationService

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
            p1.createTag(body)
        }
        else {
            resultFactory.failWithCodeAndStatus("tagService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }

    // Update
    // ------

    @RollbackOnResultFailure
    Result<ContactTag> update(Long tId, Map body) {
        findTagFromId(tId)
            .then({ ContactTag ct1 -> handleNotificationActions(ct1, body) })
            .then({ ContactTag ct1 -> doTagActions(ct1, body) })
            .then({ ContactTag ct1 -> updateTagInfo(ct1, body) })
    }
    protected Result<ContactTag> findTagFromId(Long ctId) {
        ContactTag ct1 = ContactTag.get(ctId)
        if (ct1) {
            resultFactory.success(ct1)
        }
        else {
            resultFactory.failWithCodeAndStatus("tagService.update.notFound",
                ResultStatus.NOT_FOUND, [ctId])
        }
    }
    protected Result<ContactTag> updateTagInfo(ContactTag ct1, Map body) {
        ct1.with {
            if (body.name) name = body.name
            if (body.hexColor) hexColor = body.hexColor
            if (body.language) language = Helpers.convertEnum(VoiceLanguage, body.language)
        }
        if (ct1.save()) {
            resultFactory.success(ct1)
        }
        else { resultFactory.failWithValidationErrors(ct1.errors) }
    }
    protected Result<ContactTag> handleNotificationActions(ContactTag ct1, Map body) {
        if (body.doNotificationActions) {
            Result<Void> res = notificationService.handleNotificationActions(ct1.phone,
                ct1.record.id, body.doNotificationActions)
            if (!res.success) {
                return resultFactory.failWithResultsAndStatus([res], res.status)
            }
        }
        resultFactory.success(ct1)
    }
    protected Result<ContactTag> doTagActions(ContactTag ct1, Map body) {
        if (body.doTagActions) {
            ActionContainer ac1 = new ActionContainer(body.doTagActions)
            List<ContactTagAction> actions = ac1.validateAndBuildActions(ContactTagAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
            for (ContactTagAction a1 in actions) {
                Contact c1 = a1.contact
                if (ct1.phone != c1.phone) {
                    return resultFactory.failWithCodeAndStatus("tagService.update.contactForbidden",
                        ResultStatus.FORBIDDEN, [a1.id])
                }
                switch (a1) {
                    case Constants.TAG_ACTION_ADD:
                        ct1.addToMembers(c1)
                        break
                    default: // Constants.TAG_ACTION_REMOVE
                        ct1.removeFromMembers(c1)
                }
            }
        }
        resultFactory.success(ct1)
    }

    // Delete
    // ------

    @RollbackOnResultFailure
    Result<Void> delete(Long tId) {
		ContactTag t1 = ContactTag.get(tId)
    	if (t1) {
			t1.isDeleted = true
            if (t1.save()) {
                ResultGroup<?> resGroup = new ResultGroup<>()
                // cancel all future messages
                t1.record.getFutureMessages().each { FutureMessage fMsg ->
                    resGroup << cancelFutureMessage(fMsg)
                }
                if (resGroup.anyFailures) {
                    resultFactory.failWithGroup(resGroup)
                }
                else { resultFactory.success() }
            }
            else { resultFactory.failWithValidationErrors(t1.errors) }
    	}
    	else {
    		resultFactory.failWithCodeAndStatus("tagService.delete.notFound",
                ResultStatus.NOT_FOUND, [tId])
    	}
    }

    protected Result<?> cancelFutureMessage(FutureMessage fMsg) {
        futureMessageJobService
            .unschedule(fMsg)
            .logFail("TagService.delete: unscheduling")
            .then {
                fMsg.isDone = true
                if (fMsg.save()) {
                    resultFactory.success(fMsg)
                }
                else { resultFactory.failWithValidationErrors(fMsg.errors) }
            }
    }
}
