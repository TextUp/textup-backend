package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.WebUtils
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.types.ReceiptStatus
import org.textup.util.OptimisticLockingRetry
import org.textup.validator.Author
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordNote
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
                else if (clazz == RecordCall) {
                    this.createCall(p1, body)
                }
                else { this.createNote(p1, body) }
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
        else if (body.sendToPhoneNumbers || body.sendToContacts ||
            body.sendToSharedContacts || body.sendToTags) {
            resultFactory.success(RecordText)
        }
        else if (body.forContact || body.forSharedContact || body.forTag) {
            resultFactory.success(RecordNote)
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
    protected ResultList<RecordItem> createNote(Phone p1, Map body) {
        Long cId = Helpers.toLong(body.forContact),
            scId = Helpers.toLong(body.forSharedContact),
            tId = Helpers.toLong(body.forTag)
        TempRecordNote tempNote = new TempRecordNote(phone: p1,
            contact:Contact.get(cId),
            sharedContact:SharedContact.findByContactIdAndSharedWith(scId, p1),
            tag:ContactTag.get(tId),
            after: body.after ? Helpers.toDateTimeWithZone(body.after) : null,
            info:body)
        new ResultList<RecordItem>(createOrUpdateNote(tempNote))
    }
    protected Result<RecordItem> createOrUpdateNote(TempRecordNote tempNote) {
        // also validates imageActions, if present
        if (!tempNote.doValidate()) {
            return resultFactory.failWithValidationErrors(tempNote.errors)
        }
        Author auth = authService.loggedInAndActive.toAuthor()
        RecordNote note1 = tempNote.toNote(auth)
        if (note1.save()) {
            // generate objectKeys and presigned urls for adding images, if any
            // must do this after saving so the link generated will have the note
            // id in the url. Otherwise note id will be null and will be wrong link
            tempNote
                .<String>forEachImageToAdd(note1.&addImage)
                .logFail("RecordService.createOrUpdateNote: adding image after save")
                .then({ List<String> links ->
                    getRequest().setAttribute(Constants.IMAGE_UPLOAD_KEY, links)
                })
            if (note1.location && !note1.location.save()) {
                resultFactory.failWithValidationErrors(note1.location.errors)
            }
            else if (note1.validate()) {
                resultFactory.success(note1)
            }
            else { resultFactory.failWithValidationErrors(note1.errors) }
        }
        else { resultFactory.failWithValidationErrors(note1.errors) }
    }
    protected HttpServletRequest getRequest() {
        WebUtils.retrieveGrailsWebRequest().currentRequest
    }

    // Update note
    // -----------

    Result<RecordItem> update(Long noteId, Map body) {
        RecordNote note1 = RecordNote.get(noteId)
        if (!note1) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "recordService.update.notFound", [noteId])
        }
        RecordNoteRevision rev = note1.createRevision()
        TempRecordNote tempNote = new TempRecordNote(note:note1,
            after:body.after ? Helpers.toDateTimeWithZone(body.after) : null,
            info:body)
        createOrUpdateNote(tempNote).then({ RecordItem item1 ->
            if (!rev.save()) {
                resultFactory.failWithValidationErrors(rev.errors)
            }
            else { resultFactory.success(item1) }
        }) as Result<RecordItem>
    }

    // Delete note
    // -----------

    Result delete(Long noteId) {
        RecordNote note1 = RecordNote.get(noteId)
        if (note1) {
            note1.isDeleted = true
            if (note1.save()) {
                resultFactory.success()
            }
            else { resultFactory.failWithValidationErrors(note1.errors) }
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "recordService.delete.notFound", [noteId])
        }
    }
}
