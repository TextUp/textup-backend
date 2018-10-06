package org.textup

import grails.compiler.GrailsTypeChecked

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
            Helpers.resultFactory.success(RecordCall)
        }
        else if (body.sendToPhoneNumbers || body.sendToContacts ||
            body.sendToSharedContacts || body.sendToTags) {
            Helpers.resultFactory.success(RecordText)
        }
        else if (body.forContact || body.forSharedContact || body.forTag) {
            Helpers.resultFactory.success(RecordNote)
        }
        else {
            // TODO
            Helpers.resultFactory.failWithCodeAndStatus("recordService.create.unknownType",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }

    // Building targets
    // ----------------

    static Result<OutgoingMessage> buildOutgoingMessageTarget(Phone p1, TypeConvertingMap body,
        MediaInfo mInfo = null) {

        // step 1: create each type of recipient
        ContactRecipients contacts = new ContactRecipients(phone: p1,
            ids: Helpers.allTo(Long, body.list("sendToContacts")))
        SharedContactRecipients sharedContacts = new SharedContactRecipients(phone: p1,
            ids: Helpers.allTo(Long, body.list("sendToSharedContacts")))
        ContactTagRecipients tags = new ContactTagRecipients(phone: p1,
            ids: Helpers.allTo(Long, body.list("sendToTags")))
        NumberToContactRecipients numToContacts = new NumberToContactRecipients(phone: p1,
            ids: Helpers.allTo(String, body.list("sendToPhoneNumbers")))
        ResultGroup<RecordItem> resGroup = new ResultGroup<>()
        [contacts, sharedContacts, tags, numToContacts].each { Recipients<?,?> recips ->
            if (!recips.validate()) {
                resGroup << Helpers.resultFactory.failWithValidationErrors(recips.errors)
            }
        }
        if (resGroup.anyFailures) {
            return Helpers.resultFactory.failWithResultsAndStatus(resGroup.failures,
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        // step 2: build outgoing msg
        OutgoingMessage msg1 = new OutgoingMessage(message: body.contents as String,
            media: mInfo,
            contacts: contacts.mergeRecipients(numToContacts),
            sharedContacts: sharedContacts,
            tags: tags)
        if (msg1.validate()) {
            RecordUtils.checkOutgoingMessageRecipients(msg1)
        }
        else { Helpers.resultFactory.failWithValidationErrors(msg1.errors) }
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
            return Helpers.resultFactory.failWithValidationErrors(recips.errors)
        }
        // step 2: ensure that we have at least one contactable to send to.
        // That is, ensure that the provided id actually resolved to a contactable as the check
        // in the RecordController only checks for the form of the body
        Contactable cont1 = recips.recipients[0] as Contactable
        if (cont1) {
            Helpers.resultFactory.success(cont1)
        }
        else {
            // TODO
            resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
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
            return Helpers.resultFactory.failWithValidationErrors(recips.errors)
        }
        // step 2: ensure that we have at least one entity to add the note to
        // That is, ensure that the provided id actually resolved to a entity as the check
        // in the RecordController only checks for the form of the body
        WithRecord with1 = recips.recipients[0]
        if (with1) {
            with1.tryGetRecord()
        }
        else {
            // TODO
            Helpers.resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
    }

    // Helpers
    // -------

    protected static Result<OutgoingMessage> checkOutgoingMessageRecipients(OutgoingMessage msg1) {
        // step 1: validate number of recipients
        HashSet<Contactable> recipients = msg1.toRecipients()
        Integer maxNumRecipients = Helpers.to(Integer, grailsApplication.flatConfig["textup.maxNumText"])
        if (recipients.size() > maxNumRecipients) {
            // TODO
            Helpers.resultFactory.failWithCodeAndStatus("recordService.create.tooManyForText",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        else if (recipients.isEmpty()) {
            // TODO
            Helpers.resultFactory.failWithCodeAndStatus("recordService.create.atLeastOneRecipient",
                ResultStatus.BAD_REQUEST)
        }
        else { Helpers.resultFactory.success(msg1) }
    }
}
