package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*
import org.joda.time.DateTime

@Transactional
class RecordService {

	def resultFactory
	def authService
    def lockService

    /////////////////////////////
    // Webhook utility methods //
    /////////////////////////////

    @Transactional(readOnly=true)
    boolean receiptExistsForApiId(String apiId) {
        RecordItemReceipt.findByApiId(apiId) != null
    }

    ////////////
    // Status //
    ////////////

    Result<List<RecordItemReceipt>> updateStatus(String apiId, String status, Integer duration=null) {
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
        if (receipts) { lockService.updateStatus(receipts, status, duration) }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "recordService.updateStatus.receiptsNotFound", [apiId])
        }
    }

    /////////////////////////////
    // Create new record items //
    /////////////////////////////

    Result<List<RecordText>> createIncomingRecordText(TransientPhoneNumber fromNum, Phone to,
        Map textParams, Map receiptParams) {
        createIncoming(RecordItemType.RECORD_TEXT, fromNum, to, textParams, receiptParams)
    }
    Result<List<RecordCall>> createOutgoingRecordText(Phone from, TransientPhoneNumber toNum,
        Map textParams, Map receiptParams) {
        createOutgoing(RecordItemType.RECORD_TEXT, from, toNum, textParams, receiptParams)
    }
    Result<List<RecordCall>> createIncomingRecordCall(TransientPhoneNumber fromNum, Phone to, Map receiptParams) {
        createIncoming(RecordItemType.RECORD_CALL, fromNum, to, [:], receiptParams)
    }
    Result<List<RecordCall>> createOutgoingRecordCall(Phone from, TransientPhoneNumber toNum, Map receiptParams) {
        createOutgoing(RecordItemType.RECORD_CALL, from, toNum, [:], receiptParams)
    }

    Result<RecordCall> createRecordCallForContact(long contactId, TransientPhoneNumber from,
        TransientPhoneNumber to, Integer callDuration, Map receiptParams) {

        Phone phone = Phone.forNumber(from).get()
        if (phone) {
            Contact contact = Contact.forPhoneAndContactId(phone, contactId).get()
            if (contact) {
                Map callParams = (callDuration == null) ? [:] : [durationInSeconds:callDuration]
                receiptParams.receivedBy = PhoneNumber.copy(to)
                if (receiptParams.receivedBy.validate()) {
                    lockService.addToRecordWithReceipt(RecordItemType.RECORD_CALL, true, contact, callParams, receiptParams)
                }
                else { resultFactory.failWithValidationErrors(receiptParams.receivedBy.errors) }
            }
            else {
                resultFactory.failWithMessageAndStatus(FORBIDDEN,
                    "recordService.createRecordCall.contactNotFound", [contactId, fromNum.number])
            }
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "recordService.createRecordCall.phoneNotFound", [fromNum.number])
        }
    }

    //////////////////////////////////////////
    // Creating record items helper methods //
    //////////////////////////////////////////

    protected Result<List<RecordItem>> createIncoming(RecordItemType type, TransientPhoneNumber fromNum,
        Phone to, Map itemParams, Map receiptParams) {
        Closure listAction = { -> Contact.forPhoneAndNum(to, fromNum).list() },
            createAction = { -> to.createContact([:], [fromNum.number]) }
        itemParams.outgoing = false
        receiptParams.receivedBy = to.number.copy()
        createRecordItem(type, false, listAction, createAction, itemParams, receiptParams)
    }
    protected Result<List<RecordItem>> createOutgoing(RecordItemType type, Phone from,
        TransientPhoneNumber toNum, Map itemParams, Map receiptParams) {
        Closure listAction = { -> Contact.forPhoneAndNum(from, toNum).list() },
            createAction = { -> from.createContact([:], [toNum.number]) }
        itemParams.outgoing = true
        receiptParams.receivedBy = PhoneNumber.copy(toNum)
        createRecordItem(type, true, listAction, createAction, itemParams, receiptParams)
    }
    protected Result<List<RecordItem>> createRecordItem(RecordItemType type, boolean outgoing,
        Closure listContactsAction, Closure createContactAction, Map textParams, Map receiptParams) {
        List<Contact> contacts = listContactsAction()
        if (contacts) {
            lockService.addToRecordWithReceipt(type, outgoing, contacts, textParams, receiptParams)
        }
        else {
            Result res = createContactAction()
            if (res.success) {
                Contact newContact = res.payload
                res = lockService.addToRecordWithReceipt(type, outgoing, newContact, textParams, receiptParams)
                if (res.success) { resultFactory.success([res.payload]) }
                else { res }
            }
            else { res }
        }
    }

    ////////////
    // Create //
    ////////////

    Result<RecordResult> create(Class clazz, Long id, Map body) {
        def entity = clazz.get(id)
    	Phone p1 = entity?.phone
		Result res = determineClass(body)
		if (!p1) {
			resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
				"recordService.create.noPhone")
		}
		else if (!res.success) {
			resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
				"recordService.create.unknownType")
		}
		else if (res.payload == RecordText) { createText(p1, body) }
		else if (res.payload == RecordCall) { createCall(entity, p1, body) }
		else { createNote(body) }
    }

    protected Result<RecordResult> createText(Phone p1, Map body) {
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
            else {
                //If only sending to one tag, then send through that tag
                //which will add in additional text about unsubscribing from that tag
                if (!nums && !cIds && tIds.size() == 1 && TeamContactTag.exists(tIds[0])) {
                    TeamContactTag.get(tIds.size[0]).notifySubscribers(body.contents)
                }
                else { //otherwise, send message without any special instructions from phone
                    p1.text(body.contents, nums, cIds, tIds)
                }
            }
        }
    }

    protected Result<RecordResult> createCall(def entity, Phone p1, Map body) {
        Staff staffMakingCall = entity.instanceOf(Staff) ? entity : authService.loggedIn
        if (body.callPhoneNumber && !body.callContact) {
            p1.call(staffMakingCall, Helpers.toString(body.callPhoneNumber))
        }
        else if (!body.callPhoneNumber && body.callContact) {
            p1.call(staffMakingCall, Helpers.toLong(body.callContact))
        }
        else {
            resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "recordService.create.canCallOnlyOne")
        }
    }

    protected Result<RecordResult> createNote(Map body) {
        Long cId = Helpers.toLong(body.addToContact)
        Contact c1 = Contact.get(cId)
        if (c1) {
            if (authService.hasPermissionsForContact(cId) || authService.getSharedContactForContact(cId)) {
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

    ////////////
    // Update //
    ////////////

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
