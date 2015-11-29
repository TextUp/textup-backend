package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*

@Transactional
class TagService {

	def resultFactory
    def authService

    Result<ContactTag> create(Class clazz, Long id, Map body) {
        Phone p1 = clazz.get(id)?.phone
    	if (p1) { p1.createTag(body) }
    	else {
    		resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY, 
				"tagService.create.noPhone")
    	}
    }

    Result<ContactTag> update(Long tId, Map body) {
    	ContactTag t1 = ContactTag.get(tId)
    	if (t1) {
    		//do at the beginning so we don't need to discard any field changes
    		if (body.doTagActions) {
    			def tagActions = body.doTagActions
    			if (tagActions instanceof List) {
    				for (tAction in tagActions) {
    					Contact c1 = Contact.get(Helpers.toLong(tAction.id))
    					if (!c1) {
    						return resultFactory.failWithMessageAndStatus(NOT_FOUND, 
                                "tagService.update.contactNotFound", 
                                [tAction.action, tAction.id])
    					}
    					else if (!authService.tagAndContactBelongToSame(t1.id, c1.id)) {
							return resultFactory.failWithMessageAndStatus(FORBIDDEN, 
                                "tagService.update.contactForbidden", 
                                [tAction.id])
    					}
                        Result res 
    					switch(tAction.action) {
    						case Constants.TAG_ACTION_ADD:
    							res = c1.addToTag(t1)
    							break
							case Constants.TAG_ACTION_REMOVE:
								res = c1.removeFromTag(t1)
    							break
                            case Constants.TAG_ACTION_SUBSCRIBE_CALL:
                                res = c1.subscribeToTag(t1, Constants.SUBSCRIPTION_CALL)
                                break
                            case Constants.TAG_ACTION_SUBSCRIBE_TEXT:
                                res = c1.subscribeToTag(t1, Constants.SUBSCRIPTION_TEXT)
                                break
							case Constants.TAG_ACTION_UNSUBSCRIBE:
								res = c1.unsubscribeFromTag(t1)
    							break
    						default:
                                return resultFactory.failWithMessageAndStatus(BAD_REQUEST, 
                                    "tagService.update.tagActionInvalid", 
                                    [tAction.action])
    					}
    					if (!res.success) return res
    				}
    			}
    			else {
    				return resultFactory.failWithMessageAndStatus(BAD_REQUEST, 
                        "tagService.update.tagActionNotList")
    			}
    		}
    		t1.with {
    			if (body.name) name = body.name
				if (body.hexColor) hexColor = body.hexColor
    		}
    		if (t1.save()) { resultFactory.success(t1) } 
	    	else { resultFactory.failWithValidationErrors(t1.errors) }
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND, 
    			"tagService.update.notFound", [tId])
    	}
    }

    Result delete(Long tId) {
		ContactTag t1 = ContactTag.get(tId)
    	if (t1) {
			t1.delete()
			resultFactory.success()
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND, 
    			"tagService.delete.notFound", [tId])
    	}
    }
}
