package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.textup.*
import org.textup.validator.*
import org.textup.type.*

@GrailsTypeChecked
class RecordUtils {

    static List<Class<? extends RecordItem>> parseTypes(Collection<?> rawTypes) {
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

    static Result<Class<RecordItem>> determineClass(Map body) {
        if (body.callContact || body.callSharedContact) {
            IOCUtils.resultFactory.success(RecordCall)
        }
        else if (body.sendToPhoneNumbers || body.sendToContacts ||
            body.sendToSharedContacts || body.sendToTags) {
            IOCUtils.resultFactory.success(RecordText)
        }
        else if (body.forContact || body.forSharedContact || body.forTag) {
            IOCUtils.resultFactory.success(RecordNote)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("recordUtils.determineClass.unknownType",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }

    // Building targets
    // ----------------

    static Result<RecordItemRequest> buildRecordItemRequest(Phone p1, TypeConvertingMap body,
        boolean groupByEntity) {
        Collection<Class<? extends RecordItem>> types = RecordUtils.parseTypes(body.list("types[]"))
        DateTime start = DateTimeUtils.toUTCDateTime(body.since),
            end = DateTimeUtils.toUTCDateTime(body.before)
        RecordItemRequest itemRequest = new RecordItemRequest(phone: p1,
            types: types,
            start: start,
            end: end,
            groupByEntity: groupByEntity,
            contacts: new ContactRecipients(phone: p1,
                ids: TypeConversionUtils.allTo(Long, body.list("contactIds"))),
            sharedContacts: new SharedContactRecipients(phone: p1,
                ids: TypeConversionUtils.allTo(Long, body.list("sharedContactIds"))),
            tags: new ContactTagRecipients(phone: p1,
                ids: TypeConversionUtils.allTo(Long, body.list("tagIds"))))
        if (itemRequest.validate()) {
            IOCUtils.resultFactory.success(itemRequest)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(itemRequest.errors) }
    }

    static Result<OutgoingMessage> buildOutgoingMessageTarget(Phone p1, TypeConvertingMap body,
        MediaInfo mInfo = null) {

        // step 1: create each type of recipient
        ContactRecipients contacts = new ContactRecipients(phone: p1,
            ids: TypeConversionUtils.allTo(Long, body.list("sendToContacts")))
        NumberToContactRecipients numToContacts = new NumberToContactRecipients(phone: p1,
            ids: TypeConversionUtils.allTo(String, body.list("sendToPhoneNumbers")))
        // step 2: build outgoing msg
        OutgoingMessage msg1 = new OutgoingMessage(message: body.contents as String,
            media: mInfo,
            contacts: contacts.mergeRecipients(numToContacts),
            sharedContacts: new SharedContactRecipients(phone: p1,
                ids: TypeConversionUtils.allTo(Long, body.list("sendToSharedContacts"))),
            tags: new ContactTagRecipients(phone: p1,
                ids: TypeConversionUtils.allTo(Long, body.list("sendToTags"))))
        if (msg1.validate()) {
            RecordUtils.checkOutgoingMessageRecipients(msg1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(msg1.errors) }
    }

    static Result<Contactable> buildOutgoingCallTarget(Phone p1, TypeConvertingMap body) {
        // step 1: create and validate recipients
        Recipients<Long, ? extends Contactable> recips
        if (body.callContact) {
            recips = new ContactRecipients(phone: p1, ids: [body.long("callContact")])
        }
        else { // body.callSharedContact
            recips = new SharedContactRecipients(phone: p1, ids: [body.long("callSharedContact")])
        }
        if (!recips.validate()) {
            return IOCUtils.resultFactory.failWithValidationErrors(recips.errors)
        }
        // step 2: ensure that we have at least one contactable to send to.
        // That is, ensure that the provided id actually resolved to a contactable as the check
        // in the RecordController only checks for the form of the body
        Contactable cont1 = recips.recipients[0] as Contactable
        if (cont1) {
            IOCUtils.resultFactory.success(cont1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("recordUtils.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
    }

    static Result<Record> buildNoteTarget(Phone p1, TypeConvertingMap body) {
        // step 1: create and validate recipients
        Recipients<Long, ? extends WithRecord> recips
        if (body.forSharedContact) {
            recips = new SharedContactRecipients(phone: p1, ids: [body.long("forSharedContact")])
        }
        else if (body.forContact) {
            recips = new ContactRecipients(phone: p1, ids: [body.long("forContact")])
        }
        else { // body.forTag
            recips = new ContactTagRecipients(phone: p1, ids: [body.long("forTag")])
        }
        if (!recips.validate()) {
            return IOCUtils.resultFactory.failWithValidationErrors(recips.errors)
        }
        // step 2: ensure that we have at least one entity to add the note to
        // That is, ensure that the provided id actually resolved to a entity as the check
        // in the RecordController only checks for the form of the body
        WithRecord with1 = recips.recipients[0]
        if (with1) {
            with1.tryGetRecord()
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("recordUtils.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
    }

    // Helpers
    // -------

    protected static Result<OutgoingMessage> checkOutgoingMessageRecipients(OutgoingMessage msg1) {
        Collection<Contactable> recipients = msg1.toRecipients()
        if (recipients.size() > Constants.MAX_NUM_TEXT_RECIPIENTS) {
            IOCUtils.resultFactory.failWithCodeAndStatus(
                "recordUtils.checkOutgoingMessageRecipients.tooMany",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        else if (recipients.isEmpty()) {
            IOCUtils.resultFactory.failWithCodeAndStatus("recordUtils.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
        else { IOCUtils.resultFactory.success(msg1) }
    }
}
