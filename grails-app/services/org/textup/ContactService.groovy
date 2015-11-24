package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*

@Transactional
class ContactService {

	def resultFactory
    def authService

	//////////////////
	// REST methods //
	//////////////////

	Result<Contact> create(Class clazz, Long id, Map body) {
		def owner = clazz.get(id)
        Phone p1 = owner?.phone
    	if (p1) {
    		List<String> nums = []
    		if (body.doNumberActions) {
    			Result validateRes = validateNumberActions(body.doNumberActions)
    			if (!validateRes.success) return validateRes
    			body.doNumberActions.sort { it.preference }.each {
    				if (it.action == Constants.NUMBER_ACTION_MERGE) {
    					nums << it.number
    				}
    			}
    		}
    		p1.createContact(body, nums)
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
				"contactService.create.noPhone")
    	}
	}

	Result<Contact> update(Long cId, Map body) {
		Contact c1 = Contact.get(cId)
    	if (c1) {
    		//do at the beginning so we don't need to discard any field changes
    		//number actions validate only, see below for number actions
    		List numActions = []
    		if (body.doNumberActions) {
    			Result validateRes = validateNumberActions(body.doNumberActions)
    			if (!validateRes.success) return validateRes
    			numActions = body.doNumberActions
    		}
    		//share actions
    		if (body.doShareActions) {
                def owner = c1.owner
                if (owner.instanceOf(Staff)) {
                    def shareActions = body.doShareActions
                    if (shareActions instanceof List) {
                        for (sAction in shareActions) {
                            Result res = doShareAction(c1, sAction, owner)
                            if (!res.success) return res
                        }
                    }
                    else {
                        return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                            "contactService.update.shareActionNotList")
                    }
                }
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "contactService.update.cannotShareFromTeam", [c1.id])
                }
    		}
    		//do number actions
    		for (nAction in numActions) {
    			Result res = doNumberAction(c1, nAction)
				if (!res.success) return res
			}
    		//update other fields
    		c1.with {
    			if (body.name) name = body.name
    			if (body.note) note = body.note
    			if (body.status) status = body.status
    		}
    		if (c1.save()) { resultFactory.success(c1) }
	    	else { resultFactory.failWithValidationErrors(c1.errors) }
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND,
    			"contactService.update.notFound", [cId])
    	}
	}

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

    ////////////////////
    // Helper methods //
    ////////////////////

    private Result doShareAction(Contact c1, Map sAction, Staff owner) {
    	Staff s1 = Staff.get(Helpers.toLong(sAction.id))
		if (!s1) {
			return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "contactService.update.staffNotFound",
                [sAction.action, sAction.id])
		}
		else if (!authService.canShareContactWithStaff(c1.id, s1.id)) {
			return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                "contactService.update.shareDifferentTeam",
                [sAction.id])
		}
        Result res
		switch(sAction.action) {
			case Constants.SHARE_ACTION_MERGE:
				res = owner.phone.shareContact(c1, s1.phone, sAction.permission)
				break
			case Constants.SHARE_ACTION_STOP:
				res = owner.phone.stopSharingWith(s1.phone)
				break
			default:
                return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                    "contactService.update.shareActionInvalid",
                    [sAction.action])
		}
		res
    }

    private Result doNumberAction(Contact c1, Map nAction) {
		if (nAction.action == Constants.NUMBER_ACTION_MERGE) {
			Map params = (nAction.preference != null) ? [preference:nAction.preference] : [:]
			c1.mergeNumber(nAction.number, params)
		}
		else { //else delete
			c1.deleteNumber(nAction.number)
		}
    }

    private Result validateNumberActions(def numActions) {
		if (numActions instanceof List) {
			for (nAction in numActions) {
				if (nAction.action != Constants.NUMBER_ACTION_MERGE &&
					nAction.action != Constants.NUMBER_ACTION_DELETE) {
					return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "contactService.error.numberActionInvalid",
                        [nAction.action])
				}
			}
		}
		else {
			return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "contactService.error.numberActionNotList")
		}
		resultFactory.success()
	}
}
