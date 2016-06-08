package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.hibernate.Session
import org.textup.types.ContactStatus
import org.textup.types.SharePermission
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@Transactional
class ContactService {

	ResultFactory resultFactory
    AuthService authService

    // Create
    // ------

    Result<Contact> createForTeam(Long tId, Map body) {
        create(Team.get(tId)?.phone, body)
    }
    Result<Contact> createForStaff(Map body) {
        create(authService.loggedInAndActive?.phone, body)
    }
	protected Result<Contact> create(Phone p1, Map body) {
    	if (!p1) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "contactService.create.noPhone")
        }
		List<String> nums = []
		if (body.doNumberActions) {
            Result res = validateNumberActions(body.doNumberActions)
            if (!res.success) {
                return res
            }
            (body.doNumberActions as Collection).sort {
                (it instanceof Map) ? (it as Map).preference : 0
            }.each {
                if (it instanceof Map) {
                    Map nAction = it as Map
                    if (Helpers.toLowerCaseString(nAction.action) ==
                            Constants.NUMBER_ACTION_MERGE) {
                        nums << (nAction.number as String)
                    }
                }
            }
		}
		p1.createContact(body, nums)
	}
    protected Result validateNumberActions(def numActions) {
        if (!(numActions instanceof List)) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "contactService.numberActionNotList")
        }
        for (item in numActions) {
            if (item instanceof Map) {
                Map nAction = item as Map
                if (Helpers.toLowerCaseString(nAction.action) !=
                        Constants.NUMBER_ACTION_MERGE &&
                    Helpers.toLowerCaseString(nAction.action) !=
                        Constants.NUMBER_ACTION_DELETE) {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "contactService.numberActionInvalid",
                        [nAction.action])
                }
            }
        }
        resultFactory.success()
    }

    // Update
    // ------

	Result<Contact> update(Long cId, Map body) {
        Contact c1 = Contact.get(cId)
        if (!c1) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "contactService.update.notFound", [cId])
        }
        Result.<Contact>waterfall(
            this.&handleNumberActions.curry(c1, body),
            this.&handleShareActions.rcurry(body),
            this.&updateContactInfo.rcurry(body),
        )
	}
    protected Result<Contact> updateContactInfo(Contact c1, Map body) {
        //update other fields
        c1.with {
            if (body.name) name = body.name
            if (body.note) note = body.note
            if (body.status) {
                status = Helpers.<ContactStatus>convertEnum(ContactStatus, body.status)
            }
        }
        if (c1.save()) {
            resultFactory.success(c1)
        }
        else {
            Contact.withSession { Session session -> session.clear() }
            resultFactory.failWithValidationErrors(c1.errors)
        }
    }
    protected Result<Contact> handleNumberActions(Contact c1, Map body) {
        //do at the beginning so we don't need to discard any field changes
        //number actions validate only, see below for number actions
        if (body.doNumberActions) {
            Result res = validateNumberActions(body.doNumberActions)
            if (!res.success) {
                return res
            }
            for (item in body.doNumberActions) {
                if (item instanceof Map) {
                    Map nAction = item as Map
                    if (Helpers.toLowerCaseString(nAction.action) ==
                            Constants.NUMBER_ACTION_MERGE) {
                        Map params = (nAction.preference != null) ?
                            [preference:nAction.preference] : [:]
                        res = c1.mergeNumber(nAction.number as String, params)
                    }
                    else { //else delete
                        res = c1.deleteNumber(nAction.number as String)
                    }
                    if (!res.success) {
                        return res
                    }
                }
            }
        }
        resultFactory.success(c1)
    }
    protected Result<Contact> handleShareActions(Contact c1, Map body) {
        if (body.doShareActions) {
            if (!(body.doShareActions instanceof List)) {
                return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                    "contactService.update.shareActionNotList")
            }
            for (item in body.doShareActions) {
                if (item instanceof Map) {
                    Map sAction = item as Map
                    Phone p1 = Phone.get(Helpers.toLong(sAction.id))
                    if (!p1) {
                        return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                            "contactService.update.phoneNotFound",
                            [sAction.action, sAction.id])
                    }
                    Result res
                    switch(Helpers.toLowerCaseString(sAction.action)) {
                        case Constants.SHARE_ACTION_MERGE:
                            res = c1.phone.share(c1, p1,
                                Helpers.<SharePermission>convertEnum(SharePermission,
                                    sAction.permission))
                            break
                        case Constants.SHARE_ACTION_STOP:
                            res = c1.phone.stopShare(c1, p1)
                            break
                        default:
                            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                                "contactService.update.shareActionInvalid",
                                [sAction.action])
                    }
                    if (!res.success) {
                        return res
                    }
                }
            }
        }
        resultFactory.success(c1)
    }
}
