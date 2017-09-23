package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.WebUtils
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.rest.TwimlBuilder
import org.textup.type.ReceiptStatus
import org.textup.util.OptimisticLockingRetry
import org.textup.validator.action.ActionContainer
import org.textup.validator.action.NoteImageAction
import org.textup.validator.Author
import org.textup.validator.ImageInfo
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordNote
import org.textup.validator.UploadItem

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
            resultFactory.failWithCodeAndStatus("recordService.updateStatus.receiptsNotFound",
                ResultStatus.NOT_FOUND, [apiId])
        }
    }

    // Create
    // ------

    ResultGroup<RecordItem> createForTeam(Long id, Map body) {
        create(Team.get(id)?.phone, body)
    }
    ResultGroup<RecordItem> createForStaff(Map body) {
        create(authService.loggedInAndActive?.phone, body)
    }
    protected ResultGroup<RecordItem> create(Phone p1, Map body) {
        if (p1) {
            Result<Class<RecordItem>> res = determineClass(body)
            if (res.success) {
                Class<RecordItem> clazz = res.payload
                if (clazz == RecordText) {
                    createText(p1, body)
                }
                else if (clazz == RecordCall) {
                    createCall(p1, body).toGroup()
                }
                else {
                    createNote(p1, body).toGroup()
                }
            }
            else {
                res.toGroup()
            }
        }
        else {
            resultFactory.failWithCodeAndStatus("recordService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY).toGroup()
        }
    }
    protected ResultGroup<RecordItem> createText(Phone p1, Map body) {
        List<Long> cIds = Helpers.allTo(Long, Helpers.to(List, body.sendToContacts)),
            scIds = Helpers.allTo(Long, Helpers.to(List, body.sendToSharedContacts)),
            tIds = Helpers.allTo(Long, Helpers.to(List, body.sendToTags))
        List<Contact> contacts = Contact.getAll(cIds as Iterable<Serializable>) as List
        List<PhoneNumber> pNums = Helpers.allTo(PhoneNumber, Helpers.to(List, body.sendToPhoneNumbers))
            .collect { new PhoneNumber(number:it as String) }
        // process raw phone numbers into contacts
        for (pNum in pNums) {
            if (!pNum.validate()) { // ignore invalid phone numbers
                continue
            }
            List<Contact> existing = Contact.listForPhoneAndNum(p1, pNum)
            // add existing contacts to recipients
            if (existing) {
                contacts += existing
            }
            else { // if no existing, create new contact with this number
                Result<Contact> res = p1.createContact([:], [pNum.number])
                if (res.success) {
                    contacts << res.payload
                }
                else { return res.toGroup() }
            }
        }
        // build outgoing msg and delegate subsequent actions to the phone
        OutgoingMessage msg = new OutgoingMessage(message:body.contents as String,
            contacts:contacts,
            sharedContacts:SharedContact.findEveryByContactIdsAndSharedWith(scIds, p1),
            tags:ContactTag.getAll(tIds as Iterable<Serializable>) as List)
        createTextHelper(p1, msg, authService.loggedInAndActive)
    }
    protected Result<RecordItem> createCall(Phone p1, Map body) {
        if (!Helpers.exactly(1, ["callContact", "callSharedContact"], body)) {
            return resultFactory.failWithCodeAndStatus("recordService.create.canCallOnlyOne",
                ResultStatus.BAD_REQUEST)
        }
        Contactable c1
        if (Helpers.to(Long, body.callContact)) {
            c1 = Contact.get(Helpers.to(Long, body.callContact))
        }
        else { //shared contact
            c1 = SharedContact.findByContactIdAndSharedWith(
                Helpers.to(Long, body.callSharedContact), p1)
        }
        createCallHelper(p1, c1, authService.loggedInAndActive)
    }
    protected Result<RecordItem> createNote(Phone p1, Map body) {
        Long cId = Helpers.to(Long, body.forContact),
            scId = Helpers.to(Long, body.forSharedContact),
            tId = Helpers.to(Long, body.forTag)
        TempRecordNote tempNote = new TempRecordNote(phone: p1,
            contact:Contact.get(cId),
            sharedContact:SharedContact.findByContactIdAndSharedWith(scId, p1),
            tag:ContactTag.get(tId),
            after: body.after ? Helpers.toDateTimeWithZone(body.after) : null,
            info:body)
        createOrUpdateNote(tempNote, body.doImageActions)
            .then({ RecordItem item1 -> resultFactory.success(item1, ResultStatus.CREATED) })
    }
    protected Result<RecordItem> createOrUpdateNote(TempRecordNote tempNote, Object imageActions) {
        // validate Fields
        if (!tempNote.validate()) {
            return resultFactory.failWithValidationErrors(tempNote.errors)
        }
        // validate imageActions
        List<NoteImageAction> actions = []
        if (imageActions) {
            ActionContainer ac1 = new ActionContainer(imageActions)
            actions = ac1.validateAndBuildActions(NoteImageAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
        }
        Author auth = authService.loggedInAndActive.toAuthor()
        RecordNote note1 = tempNote.toNote(auth)
        if (note1.save()) {
            // need to upload images after save so that we have an ID, otherwise
            // we won't be generating the correct object key
            Collection<String> failureMessages = []
            actions.each { NoteImageAction a1 ->
                switch (a1) {
                    case Constants.NOTE_IMAGE_ACTION_ADD:
                        Result<PutObjectResult> res = note1.addImage(new UploadItem(a1.properties))
                        if (!res.success) {
                            failureMessages += res.errorMessages
                        }
                        break
                    default: // Constants.NOTE_IMAGE_ACTION_REMOVE
                        note1.removeImage(a1.key)
                }
            }
            // any upload failures are returned on the object
            if (failureMessages) {
                getRequest().setAttribute(Constants.UPLOAD_ERRORS, failureMessages)
            }
            // after uploading, continue on with saving/validation routine
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
    // For some unknown reason, overriding methods in the Phone metaClass is very inconsistent.
    // As a result, created this helper method so that we could do an instance level override on
    // the service under test to avoid calling the actual startBridgeCall method since we are
    // focusing on testing this service in isolation in the unit test
    protected ResultGroup<RecordItem> createTextHelper(Phone p1, OutgoingMessage msg, Staff staff) {
        p1.sendMessage(msg, staff)
    }
    protected Result<RecordCall> createCallHelper(Phone p1, Contactable c1, Staff staff) {
        p1.startBridgeCall(c1, staff)
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
            after:body.after ? Helpers.toDateTimeWithZone(body.after) : null,
            info:body)
        createOrUpdateNote(tempNote, body.doImageActions).then({ RecordItem item1 ->
            List<String> dirtyProps = note1.dirtyPropertyNames
            if (!dirtyProps.isEmpty() && (dirtyProps.size() > 1 || dirtyProps[0] != "isDeleted")) {
                // update whenChanged timestamp to keep it current for any revisions
                note1.whenChanged = DateTime.now(DateTimeZone.UTC)
                // create revision of persistent values
                RecordNoteRevision rev = note1.createRevision()
                if (!rev.save()) {
                    return resultFactory.failWithValidationErrors(rev.errors)
                }
            }
            resultFactory.success(item1)
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
            resultFactory.failWithCodeAndStatus("recordService.create.unknownType",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }
}
