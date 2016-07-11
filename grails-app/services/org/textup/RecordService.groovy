package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.types.ReceiptStatus
import org.textup.util.OptimisticLockingRetry
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class RecordService {

	ResultFactory resultFactory
	AuthService authService
    SocketService socketService
    TwimlBuilder twimlBuilder

    // Status
    // ------

    @OptimisticLockingRetry
    Result<Closure> updateStatus(ReceiptStatus status, String apiId, Integer duration=null) {
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
        if (receipts) {
            List<RecordItem> items = []
            for (receipt in receipts) {
                RecordItem item = receipt.item
                receipt.status = status
                if (duration && item.instanceOf(RecordCall)) {
                    (item as RecordCall).durationInSeconds = duration
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
            Result<Class<RecordItem>> res = this.determineClass(body)
            if (res.success) {
                Class<RecordItem> clazz = res.payload
                if (clazz == RecordText) {
                    this.createText(p1, body)
                }
                else {
                    this.createCall(p1, body)
                }
            }
            else { new ResultList(res) }
        }
        else {
            new ResultList(resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "recordService.create.noPhone"))
        }
    }
    protected Result<Class<RecordItem>> determineClass(Map body) {
        if (body.callContact || body.callSharedContact) {
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
    protected ResultList<RecordItem> createText(Phone p1, Map body) {
        List<Long> cIds = Helpers.toIdsList(body.sendToContacts),
            scIds = Helpers.toIdsList(body.sendToSharedContacts),
            tIds = Helpers.toIdsList(body.sendToTags)
        List<Contact> contacts = Contact.getAll(cIds as Iterable<Serializable>) as List
        List<PhoneNumber> pNums = Helpers.toList(body.sendToPhoneNumbers)
            .collect { new PhoneNumber(number:it as String) }
        // process raw phone numbers into contacts
        for (pNum in pNums) {
            if (!pNum.validate()) { continue } // ignore invalid phone numbers
            List<Contact> existing = Contact.listForPhoneAndNum(p1, pNum)
            // add existing contacts to recipients
            if (existing) { contacts += existing }
            else { // if no existing, create new contact with this number
                Result<Contact> res = p1.createContact([:], [pNum.number])
                if (res.success) {
                    contacts << res.payload
                }
                else { return new ResultList(res) }
            }
        }
        // build outgoing msg and delegate subsequent actions to the phone
        OutgoingMessage msg = new OutgoingMessage(message:body.contents as String,
            contacts:contacts,
            sharedContacts:SharedContact.findEveryByContactIdsAndSharedWith(scIds, p1),
            tags:ContactTag.getAll(tIds as Iterable<Serializable>) as List)
        p1.sendMessage(msg, authService.loggedInAndActive)
    }
    protected ResultList<RecordItem> createCall(Phone p1, Map body) {
        ResultList resList = new ResultList()
        if (!Helpers.exactly(1, ["callContact", "callSharedContact"], body)) {
            return resList << resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "recordService.create.canCallOnlyOne")
        }
        Contactable c1
        if (Helpers.toLong(body.callContact)) {
            c1 = Contact.get(Helpers.toLong(body.callContact))
        }
        else { //shared contact
            c1 = SharedContact.findByContactIdAndSharedWith(
                Helpers.toLong(body.callSharedContact), p1)
        }
        p1.startBridgeCall(c1, authService.loggedInAndActive)
    }
}
