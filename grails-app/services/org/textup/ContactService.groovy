package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*

@Transactional
class ContactService {

	def resultFactory
    def authService

    // Create
    // ------

    Result<Contact> createForTeam(Long tId, Map body) {
        create(Team.get(tId)?.phone, body)
    }
    Result<Contact> createForStaff(Map body) {
        create(authService.loggedInAndActive?.phone, body)
    }
	Result<Contact> create(Phone p1, Map body) {
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
            body.doNumberActions.sort { it.preference }.each {
                if (it.action == Constants.NUMBER_ACTION_MERGE) {
                    nums << it.number
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
        for (nAction in numActions) {
            if (nAction.action != Constants.NUMBER_ACTION_MERGE &&
                nAction.action != Constants.NUMBER_ACTION_DELETE) {
                return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                    "contactService.numberActionInvalid",
                    [nAction.action])
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
        Result res = Result.waterfall(
            this.&handleNumberActions.curry(c1, body),
            this.&handleShareActions.rcurry(body)
        )
        if (res.success) {
            //update other fields
            c1.with {
                if (body.name) name = body.name
                if (body.note) note = body.note
                if (body.status) status = body.status
            }
            if (c1.save()) {
                resultFactory.success(c1)
            }
            else { resultFactory.failWithValidationErrors(c1.errors) }
        }
        else {
            Contact.withSession { it.clear() }
            return res
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
            for (nAction in body.doNumberActions) {
                if (nAction.action == Constants.NUMBER_ACTION_MERGE) {
                    Map params = (nAction.preference != null) ?
                        [preference:nAction.preference] : [:]
                    c1.mergeNumber(nAction.number, params)
                }
                else { //else delete
                    c1.deleteNumber(nAction.number)
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
            for (sAction in body.doShareActions) {
                Phone p1 = Staff.get(Helpers.toLong(sAction.id))
                if (!p1) {
                    return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                        "contactService.update.phoneNotFound",
                        [sAction.action, sAction.id])
                }
                Result res
                switch(sAction.action) {
                    case Constants.SHARE_ACTION_MERGE:
                        if (!c1.phone.canShare(p1)) {
                            return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                                "contactService.update.shareForbidden",
                                [sAction.id])
                        }
                        res = c1.phone.share(c1, p1,
                            Helpers.convertEnum(SharePermission, sAction.permission))
                        break
                    case Constants.SHARE_ACTION_STOP:
                        res = c1.phone.stopShare(c1, p1)
                        break
                    default:
                        return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                            "contactService.update.shareActionInvalid",
                            [sAction.action])
                }
                if (!res.success) { return res }
            }
        }
        resultFactory.success(c1)
    }

    // Delete
    // ------

	Result delete(Long cId) {
		Contact c1 = Contact.get(cId)
    	if (c1) {
			c1.delete()
			resultFactory.success()
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND,
    			"contactService.delete.notFound", [cId])
    	}
    }
}
