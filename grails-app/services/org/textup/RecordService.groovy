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

    Result<List<RecordItemReceipt>> updateStatus(String apiId, String status, Integer duration=null) {
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
        if (receipts) { lockService.updateStatus(receipts, status, duration) }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "recordService.updateStatus.receiptsNotFound", [apiId])
        }
    }

    /////////////////////////////////
    // Webhook for calls and texts //
    /////////////////////////////////

    Result<List<RecordText>> createIncomingRecordText(PhoneNumber fromNum, Phone to,
        Map textParams, Map receiptParams) {
        createIncoming(RecordItemType.RECORD_TEXT, fromNum, to, textParams, receiptParams)
    }
    Result<List<RecordCall>> createOutgoingRecordCall(Phone from, PhoneNumber toNum,
        Map textParams, Map receiptParams) {
        createOutgoing(RecordItemType.RECORD_TEXT, from, toNum, textParams, receiptParams)
    }
    Result<List<RecordCall>> createIncomingRecordCall(PhoneNumber fromNum, Phone to, Map receiptParams) {
        createIncoming(RecordItemType.RECORD_CALL, fromNum, to, [:], receiptParams)
    }
    Result<List<RecordCall>> createOutgoingRecordCall(Phone from, PhoneNumber toNum, Map receiptParams) {
        createOutgoing(RecordItemType.RECORD_CALL, from, toNum, [:], receiptParams)
    }

    protected Result<List<RecordText>> createIncoming(RecordItemType type, PhoneNumber fromNum,
        Phone to, Map itemParams, Map receiptParams) {
        Closure list = { -> Contact.forPhoneAndNum(to, fromNum.number).list() },
            create = { -> to.createContact([:], [fromNum.number]) }
        itemParams.outgoing = false
        receiptParams.receivedBy = to.number.copy()
        createRecordItem(type, list, create, itemParams, receiptParams)
    }
    protected Result<List<RecordCall>> createOutgoing(RecordItemType type, Phone from,
        PhoneNumber toNum, Map itemParams, Map receiptParams) {
        Closure list = { -> Contact.forPhoneAndNum(from, toNum.number).list() },
            create = { -> from.createContact([:], [toNum.number]) }
        itemParams.outgoing = true
        receiptParams.receivedBy = toNum.save()
        createRecordItem(type, list, create, itemParams, receiptParams)
    }
    protected Result<List<RecordCall>> createRecordItem(RecordItemType type, Closure listContacts,
        Closure createContact, Map textParams, Map receiptParams) {
        List<Contact> contacts = listContacts()
        if (contacts) {
            lockService.addToRecordWithReceipt(type, contacts, textParams, receiptParams)
        }
        else {
            Result res = createContact()
            if (res.success) {
                Contact newContact = res.payload
                res = lockService.addToRecordWithReceipt(type, newContact, textParams, receiptParams)
                if (res.success) { resultFactory.success([res.payload]) }
                else { res }
            }
            else { res }
        }
    }

    ///////////////////////
    // Webhook for calls //
    ///////////////////////

    Result<RecordCall> createRecordCallForContact(long contactId, String from, String to,
        Integer callDuration, Map receiptParams) {

        Phone phone = Phone.forNumber(Helpers.cleanNumber(from)).get()
        if (phone) {
            Contact contact = Contact.forPhoneAndContactId(phone, contactId).get()
            if (contact) {
                Map callParams = (callDuration == null) ? [:] : [durationInSeconds:callDuration]
                receiptParams.receivedBy = new PhoneNumber(number:to)
                if (receiptParams.receivedBy.validate()) {
                    lockService.addToRecordWithReceipt(RecordItemType.RECORD_CALL, contact, callParams, receiptParams)
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

    //////////////////
    // REST methods //
    //////////////////

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
