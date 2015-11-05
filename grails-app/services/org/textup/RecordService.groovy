package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*

@Transactional
class RecordService {

	def resultFactory
	def authService

    Result<RecordResult> create(Class clazz, Long id, Map body) {
    	Phone p1 = clazz.get(id)?.phone
		Result res = determineClass(body)
		if (!p1) {
			resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY, 
				"recordService.create.noPhone")
		}
		else if (!res.success) {
			resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY, 
				"recordService.create.unknownType")
		}
		else if (res.payload == RecordText) {
			Result cRes = toIdsList(body.sendToContacts)
			if (!cRes.success) return cRes 
			Result tRes = toIdsList(body.sendToTags)
			if (!tRes.success) return tRes 
			List<String> nums = Helpers.toList(body.sendToPhoneNumbers)
			List<Long> cIds = cRes.payload, tIds = tRes.payload
			if ([nums, cIds, tIds].every { it.isEmpty() }) {
				resultFactory.failWithMessageAndStatus(BAD_REQUEST, 
					"recordService.create.noTextRecipients")
			}
			else {
				Boolean isFuture = Helpers.toBoolean(body.futureText)
				if (isFuture == true) {
					p1.scheduleText(body.contents, body.sendAt, nums, cIds, tIds)
				}
				else { p1.text(body.contents, nums, cIds, tIds) }
			}
		}
		else if (res.payload == RecordCall) {
			if (body.callPhoneNumber && !body.callContact) {
				p1.call(Helpers.toString(body.callPhoneNumber))
			}
			else if (!body.callPhoneNumber && body.callContact) {
				p1.call(Helpers.toLong(body.callContact))
			}
			else {
				resultFactory.failWithMessageAndStatus(BAD_REQUEST, 
					"recordService.create.canCallOnlyOne")
			}
		}
		else { //is RecordNote
			Long cId = Helpers.toLong(body.addToContact)
			Contact c1 = Contact.get(cId)
			if (c1) { 
				if (authService.hasPermissionsForContact(cId) || 
					authService.getSharedContactForContact(cId)) {
					c1.addNote(body)
				}
				else {
					resultFactory.failWithMessageAndStatus(FORBIDDEN, 
						"recordService.create.contactForbidden", [cId])
				}
			}
			else {
				resultFactory.failWithMessageAndStatus(NOT_FOUND, 
					"recordService.create.contactNotFound", [cId])
			}
		}
    }

    Result<RecordItem> update(Long id, Map body) {
    	RecordItem rItem = RecordItem.get(id) 
    	if (rItem) {
    		Staff loggedIn = authService.getLoggedIn()
			if (rItem.instanceOf(RecordNote) && rItem.editable) {
				rItem.with {
					note = body.note 
					authorName = loggedIn.name
					authorId = loggedIn.id
				}
				if (rItem.save()) { resultFactory.success(rItem) }
				else { resultFactory.failWithValidationErrors(rItem.errors) }
			}
			else if (rItem.instanceOf(RecordText) && !body.contents) {
				Boolean futureBool = Helpers.toBoolean(body.futureText)
				if (futureBool == false) { rItem.cancelScheduled() }
				//setting sendAt also automatically sets futureText
				if (body.sendAt) { rItem.sendAt = body.sendAt }
				
				if (rItem.save()) { resultFactory.success(rItem) }
				else { resultFactory.failWithValidationErrors(rItem.errors) }
			}
    		else {
    			resultFactory.failWithMessageAndStatus(FORBIDDEN, 
    				"recordService.update.notEditable", [id])
    		}
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND, 
    			"recordService.update.itemNotFound", [id])
    	}
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    private Result<Class<RecordItem>> determineClass(Map body) {
    	if (body.callContact || body.callPhoneNumber) { resultFactory.success(RecordCall) }
    	else if (body.contents) { resultFactory.success(RecordText) }
    	else if (body.note) { resultFactory.success(RecordNote) }
    	else {
    		resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY, 
    			"recordService.create.unknownType")
    	}
    }
    private Result<List<Long>> toIdsList(def data) {
    	List rawIds = Helpers.toList(data)
    	List<Long> ids = []
    	for (rawId in rawIds) {
    		Long id = Helpers.toLong(rawId)
    		if (id) { ids << id }
    		else {
    			return resultFactory.failWithMessageAndStatus(BAD_REQUEST, 
    			"recordService.create.idIsNotLong", [rawId])
    		}
		}
		resultFactory.success(ids)
    }
}
