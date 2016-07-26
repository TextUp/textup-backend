package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.hibernate.Session
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@Transactional
class TagService {

	ResultFactory resultFactory
    AuthService authService

    // Create
    // ------

    Result<ContactTag> createForTeam(Long tId, Map body) {
        create(Team.get(tId)?.phone, body)
    }
    Result<ContactTag> createForStaff(Map body) {
        create(authService.loggedInAndActive?.phone, body)
    }
    protected Result<ContactTag> create(Phone p1, Map body) {
        if (p1) {
            p1.createTag(body)
        }
        else {
            resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "tagService.create.noPhone")
        }
    }

    // Update
    // ------

    Result<ContactTag> update(Long tId, Map body) {
        Result.<ContactTag>waterfall(
            this.&findTagFromId.curry(tId),
            //do at the beginning so we don't need to discard any field changes
            this.&doTagActions.rcurry(body),
            this.&updateTagInfo.rcurry(body)
        ).then({ ContactTag ct1 ->
            if (ct1.save()) {
                resultFactory.success(ct1)
            }
            else {
                ContactTag.withSession { Session session -> session.clear() }
                resultFactory.failWithValidationErrors(ct1.errors)
            }
        }) as Result
    }
    protected Result<ContactTag> findTagFromId(Long ctId) {
        ContactTag ct1 = ContactTag.get(ctId)
        if (ct1) {
            resultFactory.success(ct1)
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "tagService.update.notFound", [ctId])
        }
    }
    protected Result<ContactTag> updateTagInfo(ContactTag ct1, Map body) {
        ct1.with {
            if (body.name) name = body.name
            if (body.hexColor) hexColor = body.hexColor
        }
        if (ct1.save()) {
            resultFactory.success(ct1)
        }
        else { resultFactory.failWithValidationErrors(ct1.errors) }
    }
    protected Result<ContactTag> doTagActions(ContactTag ct1, Map body) {
        if (!body.doTagActions) {
            return resultFactory.success(ct1)
        }
        if (body.doTagActions && !(body.doTagActions instanceof List)) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "tagService.update.tagActionNotList")
        }
        for (item in body.doTagActions) {
            if (!(item instanceof Map)) { continue }
            Map tAction = item as Map
            Contact c1 = Contact.get(Helpers.toLong(tAction.id))
            if (!c1) {
                return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                    "tagService.update.contactNotFound",
                    [tAction.action, tAction.id])
            }
            else if (ct1.phone != c1.phone) {
                return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                    "tagService.update.contactForbidden",
                    [tAction.id])
            }
            switch(Helpers.toLowerCaseString(tAction.action)) {
                case Constants.TAG_ACTION_ADD:
                    ct1.addToMembers(c1)
                    break
                case Constants.TAG_ACTION_REMOVE:
                    ct1.removeFromMembers(c1)
                    break
                default:
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "tagService.update.tagActionInvalid",
                        [tAction.action])
            }
        }
        resultFactory.success(ct1)
    }

    // Delete
    // ------

    Result delete(Long tId) {
		ContactTag t1 = ContactTag.get(tId)
    	if (t1) {
			t1.isDeleted = true
            if (t1.save()) {
                // cancel all future messages
                t1.record.getFutureMessages().each({ FutureMessage fMsg ->
                    fMsg.cancel()
                    fMsg.save()
                })
                resultFactory.success()
            }
            else { resultFactory.failWithValidationErrors(t1.errors) }
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND,
    			"tagService.delete.notFound", [tId])
    	}
    }
}
