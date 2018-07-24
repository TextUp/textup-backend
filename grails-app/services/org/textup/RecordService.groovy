package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.util.WebUtils
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.rest.TwimlBuilder
import org.textup.type.ReceiptStatus
import org.textup.util.OptimisticLockingRetry
import org.textup.validator.action.ActionContainer
import org.textup.validator.action.MediaAction
import org.textup.validator.Author
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordNote
import org.textup.validator.UploadItem

@GrailsTypeChecked
@Transactional
class RecordService {

    AuthService authService
    ResultFactory resultFactory
    GrailsApplication grailsApplication
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
            resultFactory.failWithCodeAndStatus("recordService.updateStatus.receiptsNotFound",
                ResultStatus.NOT_FOUND, [apiId])
        }
    }

    // Create
    // ------

    ResultGroup<RecordItem> create(Long phoneId, Map body) {
        Phone p1 = Phone.get(phoneId)
        if (!p1) {
            return resultFactory.failWithCodeAndStatus("recordService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY).toGroup()
        }
        Result<Class<RecordItem>> res = determineClass(body)
        if (!res.success) {
            return res.toGroup()
        }
        switch(res.payload) {
            case RecordText: createText(p1, body); break;
            case RecordCall: createCall(p1, body).toGroup(); break;
            default RecordNote: createNote(p1, body).toGroup()
        }
    }
    protected ResultGroup<RecordItem> createText(Phone p1, Map body) {
        // step 1: build outgoing message and check to see if # recipients falls within bounds
        Result<OutgoingMessage> msgRes = buildOutgoingMessage(p1, body)
            .then { OutgoingMessage msg1 -> checkOutgoingMessageRecipients(msg1) }
        if (msgRes.success) {
            // step 2: handle media upload, storing upload errors on request
            Collection<String> errorMsgs = []
            Result<MediaInfo> mediaRes = mediaService.buildMediaInfo(new MediaInfo(), errorMsgs, body)
            if (mediaRes.success) {
                storeErrorsOnRequest(errorMsgs)
                // step 3: actually send outgoing message
                createTextHelper(p1, msgRes.payload, mediaRes.payload, authService.loggedInAndActive)
            }
            else { mediaRes.toGroup() }
        }
        else { msgRes.toGroup() }
    }
    protected Result<OutgoingMessage> buildOutgoingMessage(Phone p1, Map body) {
        // step 1: create each type of recipient
        ContactRecipients contacts = new ContactRecipients(phone: p1,
            ids: Helpers.allTo(Long, Helpers.to(List, body.sendToContacts)))
        SharedContactRecipients sharedContacts = new SharedContactRecipients(phone: p1,
            ids: Helpers.allTo(Long, Helpers.to(List, body.sendToSharedContacts)))
        ContactTagRecipients tags = new ContactTagRecipients(phone: p1,
            ids: Helpers.allTo(Long, Helpers.to(List, body.sendToTags)))
        NumberToContactRecipients numToContacts = new NumberToContactRecipients(phone: p1,
            ids: Helpers.allTo(String, Helpers.to(List, body.sendToPhoneNumbers)))
        ResultGroup<RecordItem> resGroup = new ResultGroup<>()
        [contacts, sharedContacts, tags, numToContacts].each { Recipients<?,?> recips ->
            if (!recips.validate()) {
                resGroup << resultFactory.failWithValidationErrors(recips.errors)
            }
        }
        if (resGroup.anyFailures) {
            return resGroup
        }
        // step 2: build outgoing msg
        OutgoingMessage msg1 = new OutgoingMessage(message:body.contents as String,
            contacts:contacts.merge(numToContacts),
            sharedContacts:sharedContacts,
            tags:tags)
        if (msg1.validate()) {
            resultFactory.success(msg1)
        }
        else { resultFactory.failWithValidationErrors(msg1.errors) }
    }
    protected Result<OutgoingMessage> checkOutgoingMessageRecipients(OutgoingMessage msg1) {
        // step 1: validate number of recipients
        HashSet<Contactable> recipients = msg1.toRecipients()
        Integer maxNumRecipients = Helpers.to(Integer, grailsApplication.flatConfig["textup.maxNumText"])
        if (recipients.size() > maxNumRecipients) {
            return resultFactory.failWithCodeAndStatus("recordService.create.tooManyForText",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        if (recipients.isEmpty()) {
            return resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
        resultFactory.success(msg1)
    }
    // For some unknown reason, overriding methods in the Phone metaClass is very inconsistent.
    // As a result, created this helper method so that we could do an instance level override on
    // the service under test to avoid calling the actual startBridgeCall method since we are
    // focusing on testing this service in isolation in the unit test
    protected ResultGroup<RecordItem> createTextHelper(Phone p1, OutgoingMessage msg,
        MediaInfo mInfo, Staff staff) {
        p1.sendMessage(msg, mInfo, staff)
    }

    protected Result<RecordItem> createCall(Phone p1, Map body) {
        // step 1: create and validate recipients
        Recipients<Long, ? extends Contactable> recips = body.callContact ?
            new ContactRecipients(phone: p1, ids: [Helpers.to(Long, body.callContact)]) :
            new SharedContactRecipients(phone: p1, ids: [Helpers.to(Long, body.callSharedContact)])
        if (!recips.validate()) {
            return resultFactory.failWithValidationErrors(recips.errors)
        }
        // step 2: ensure that we have at least one contactable to send to.
        // That is, ensure that the provided id actually resolved to a contactable as the check
        // in the RecordController only checks for the form of the body
        Contactable cont1 = recips.recipients[0]
        if (!cont1) {
            return resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
        createCallHelper(p1, cont1, authService.loggedInAndActive)
    }
    // for easier mocking during testing
    protected Result<RecordCall> createCallHelper(Phone p1, Contactable c1, Staff staff) {
        p1.startBridgeCall(c1, staff)
    }

    protected Result<RecordItem> createNote(Phone p1, Map body) {
        // step 1: create and validate recipients
        Recipients<Long, ? extends WithRecord> recips = body.forSharedContact ?
            new SharedContactRecipients(phone: p1, ids: [Helpers.to(Long, body.forSharedContact)]) :
                body.forContact ?
                    new ContactRecipients(phone: p1, ids: [Helpers.to(Long, body.forContact)]) :
                        new ContactTagRecipients(phone: p1, ids: [Helpers.to(Long, body.forTag)])
        if (!recips.validate()) {
            return resultFactory.failWithValidationErrors(recips.errors)
        }
        // step 2: ensure that we have at least one entity to add the note to
        // That is, ensure that the provided id actually resolved to a entity as the check
        // in the RecordController only checks for the form of the body
        WithRecord with1 = recips.recipients[0]
        if (!with1) {
            return resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
        // step 3: create validator object for note
        TempRecordNote tempNote = new TempRecordNote(note: new RecordNote(record:with1.record),
            info: body,
            after: body.after ? Helpers.toDateTimeWithZone(body.after) : null)
        Collection<String> errorMsgs = []
        tempNote.toNote(authService.loggedInAndActive.toAuthor())
            // step 4: handle media actions
            .then({ RecordNote note1 ->
                mediaService.<RecordNote>handleMediaActions(note1, errorMsgs, body)
            })
            // step 5: handle upload errors + actually save the note
            .then({ RecordNote note1 ->
                storeErrorsOnRequest(errorMsgs)
                resultFactory.success(note1, ResultStatus.CREATED)
            })
    }
    protected void storeErrorsOnRequest(Collection<String> errorMsgs) {
        WebUtils.retrieveGrailsWebRequest()
            ?.currentRequest
            ?.setAttribute(Constants.UPLOAD_ERRORS, errorMsgs)
    }

    // Update note
    // -----------

    Result<RecordItem> update(Long noteId, Map body) {
        RecordNote note1 = RecordNote.get(noteId)
        if (!note1) {
            return resultFactory.failWithCodeAndStatus("recordService.update.notFound",
                ResultStatus.NOT_FOUND, [noteId])
        }
        if (note1.isReadOnly) {
            return resultFactory.failWithCodeAndStatus("recordService.update.readOnly",
                ResultStatus.FORBIDDEN, [noteId])
        }
        TempRecordNote tempNote = new TempRecordNote(note:note1,
            info:body
            after:body.after ? Helpers.toDateTimeWithZone(body.after) : null)
        Collection<String> errorMsgs = []
        tempNote.toNote(authService.loggedInAndActive.toAuthor())
            .then({ RecordNote note1 ->
                mediaService.<RecordNote>handleMediaActions(note1, errorMsgs, body)
            })
            .then({ RecordNote note1 ->
                storeErrorsOnRequest(errorMsgs)
                note1.tryCreateRevision()
            })
    }

    // Delete note
    // -----------

    Result<Void> delete(Long noteId) {
        RecordNote note1 = RecordNote.get(noteId)
        if (note1) {
            if (note1.isReadOnly) {
                return resultFactory.failWithCodeAndStatus("recordService.delete.readOnly",
                    ResultStatus.FORBIDDEN, [noteId])
            }
            note1.isDeleted = true
            if (note1.save()) {
                resultFactory.success()
            }
            else { resultFactory.failWithValidationErrors(note1.errors) }
        }
        else {
            resultFactory.failWithCodeAndStatus("recordService.delete.notFound",
                ResultStatus.NOT_FOUND, [noteId])
        }
    }

    // Identification
    // --------------

    List<Class<? extends RecordItem>> parseTypes(Collection<?> rawTypes) {
        if (!rawTypes) {
            return []
        }
        HashSet<Class<? extends RecordItem>> types = new HashSet<>()
        rawTypes.each { Object obj ->
            switch (obj as String) {
                case "text": types << RecordText; break;
                case "call": types << RecordCall; break;
                case "note": types << RecordNote
            }
        }
        new ArrayList<Class<? extends RecordItem>>(types)
    }

    Result<Class<RecordItem>> determineClass(Map body) {
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
            resultFactory.failWithCodeAndStatus("recordService.create.unknownType",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }
}
