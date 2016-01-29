package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*
import org.joda.time.DateTime

@Transactional
class RecordService {

	def resultFactory
	def authService
    def socketService
    def twimlBuilder

    // Status
    // ------

    Result<Closure> updateStatus(ReceiptStatus status, String apiId, Integer duration=null) {
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
        if (receipts) {
            List<RecordItem> items = []
            for (receipt in receipts) {
                RecordItem item = receipt.item
                receipt.status = status
                if (duration && item.instanceOf(RecordCall)) {
                    item.durationInSeconds = duration
                }
                if (!receipt.save()) {
                    return resultFactory.failWithValidationErrors(receipt.errors)
                }
                if (!item.save()) {
                    return resultFactory.failWithValidationErrors(item.errors)
                }
                items << item
            }
            //send items with updated status through socket
            socketService.sendItems(items, Constants.SOCKET_EVENT_RECORD_STATUSES)
            twimlBuilder.noResponse()
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "recordService.updateStatus.receiptsNotFound", [apiId])
        }
    }

    // Create
    // ------

    ResultList<RecordItem> createForTeam(Long id, Map body) {
        this.create(Team.get(id)?.phone, body)
    }
    ResultList<RecordItem> createForStaff(Map body) {
        this.create(authService.loggedInAndActive?.phone, body)
    }
    protected ResultList<RecordItem> create(Phone p1, Map body) {
        if (p1) {
            this.determineClass(body).then({ Class<RecordItem> clazz ->
                if (res.payload == RecordText) {
                    this.createText(p1, body)
                }
                else {
                    this.createCall(entity, p1, body)
                }
            })
        }
        else {
            resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "recordService.create.noPhone")
        }
    }
    protected Result<Class<RecordItem>> determineClass(Map body) {
        if (body.callContact || body.callPhoneNumber) {
            resultFactory.success(RecordCall)
        }
        else if (body.contents) {
            resultFactory.success(RecordText)
        }
        else {
            resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "recordService.create.unknownType")
        }
    }
    protected ResultList<RecordText> createText(Phone p1, Map body) {
        Long<Long> cIds = Helpers.toIdsList(body.sendToContacts),
            scIds = Helpers.toIdsList(body.sendToSharedContacts),
            tIds = Helpers.toIdsList(body.sendToTags)
        List<String> nums = Helpers.toList(body.sendToPhoneNumbers)
        if ([nums, cIds, scIds, tIds].every { it.isEmpty() }) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "recordService.create.noTextRecipients")
        }
        OutgoingText text = new OutgoingText(message:body.contents,
            contacts: Contact.getAll(cIds),
            sharedContacts: SharedContact.findByContactIds(scIds),
            tags:ContactTag.getAll(tIds))
        p1.sendText(text, authService.loggedInAndActive)
    }
    protected ResultList<RecordCall> createCall(Phone p1, Map body) {
        if (!Helpers.exactly(1, ["callPhoneNumber", "callContact",
            "callSharedContact"], body)) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "recordService.create.canCallOnlyOne")
        }
        Contactable c1
        if (Helpers.toString(body.callPhoneNumber)) {
            Result<Contact> res = p1.createContact([:],
                [Helpers.toString(body.callPhoneNumber)])
            if (res.success) {
                c1 = res.payload
            }
            else { return res  }
        }
        else if (Helpers.toLong(body.callContact)) {
            c1 = Contact.get(Helpers.toLong(body.callContact))
        }
        else { //shared contact
            c1 = SharedContact.findByContactId(Helpers.toLong(body.callSharedContact))
        }
        p1.startBridgeCall(c1, authService.loggedInAndActive)
    }
}
