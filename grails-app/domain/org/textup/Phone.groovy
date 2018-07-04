package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.rest.TwimlBuilder
import org.textup.type.CallResponse
import org.textup.type.ContactStatus
import org.textup.type.PhoneOwnershipType
import org.textup.type.ReceiptStatus
import org.textup.type.RecordItemType
import org.textup.type.SharePermission
import org.textup.type.TextResponse
import org.textup.type.VoiceLanguage
import org.textup.type.VoiceType
import org.textup.validator.BasePhoneNumber
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@EqualsAndHashCode(excludes="owner")
@RestApiObject(name="Phone", description="A TextUp phone.")
class Phone {

    ResultFactory resultFactory
    PhoneService phoneService
    TwimlBuilder twimlBuilder

    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)

    //unique id assigned to this phone's number
    String apiId
	String numberAsString
    PhoneOwnership owner

    @RestApiObjectField(
            apiFieldName  = "awayMessage",
            description   = "Away message when no staff members in this team \
                are available to respond to texts or calls",
            allowedType   = "String")
    String awayMessage = Constants.DEFAULT_AWAY_MESSAGE

    @RestApiObjectField(
        description  = "Voice to use when speaking during calls. Allowed: MALE, FEMALE",
        mandatory    = false,
        allowedType  = "String",
        defaultValue = "MALE")
    VoiceType voice = VoiceType.MALE

    @RestApiObjectField(
        description  = "Language to use when speaking during calls. Allowed: \
            CHINESE, ENGLISH, FRENCH, GERMAN, ITALIAN, JAPANESE, KOREAN, PORTUGUESE, RUSSIAN, SPANISH",
        mandatory    = false,
        allowedType  = "String",
        defaultValue = "ENGLISH")
    VoiceLanguage language = VoiceLanguage.ENGLISH

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "number",
            description  = "Phone number of this TextUp phone.",
            allowedType  = "String"),
         @RestApiObjectField(
            apiFieldName      = "mandatoryEmergencyMessage",
            description       = "Mandatory emergency message that will be appended to the custom \
                away message to bring the total character length to no more than 160 characters.",
            allowedType       = "String",
            useForCreation    = false),
        @RestApiObjectField(
            apiFieldName      = "doPhoneActions",
            description       = "List of some actions to perform on this phone",
            allowedType       = "List<[phoneAction]>",
            useForCreation    = false,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "tags",
            description    = "List of tags, if any.",
            allowedType    = "List<Tag>",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "availability",
            description    = "Contains availability info for a particular staff for this phone, \
                update attributes in this object to update phone-specific availability",
            allowedType    = "StaffPolicyAvailability",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "others",
            description    = "READ ONLY, full availability information for other staff owners of this \
                particular phone if this is a team phone.",
            allowedType    = "List<StaffPolicyAvailability>",
            useForCreation = false)
    ])
    static transients = ["number", "resultFactory", "phoneService", "twimlBuilder"]
    static constraints = {
        apiId blank:true, nullable:true, unique:true
        voice blank:false, nullable:false
        numberAsString blank:true, nullable:true, validator:{ String num, Phone obj ->
            if (!num) { // short circuit if number is blank
                return;
            }
            //phone number must be unique for phones
            Closure<Boolean> existsWithSameNumber = {
                Phone ph = Phone.findByNumberAsString(new PhoneNumber(number:num).number)
                ph && ph.id != obj.id
            }
            if (Helpers.<Boolean>doWithoutFlush(existsWithSameNumber)) {
                return ["duplicate"]
            }
            if (!(num.toString() ==~ /^(\d){10}$/)) {
                return ["format"]
            }
        }
        awayMessage blank:false, size:1..(Constants.TEXT_LENGTH)
    }
    static mapping = {
        whenCreated type:PersistentDateTime
        owner fetch:"join", cascade:"all-delete-orphan"
    }

    /*
	Has many:
		Contact
		ContactTag
	*/

    // Hooks
    // -----

    def beforeValidate() {
        String mandatoryMsg = Constants.AWAY_EMERGENCY_MESSAGE,
            awayMsg = this.awayMessage ?: ""
        int maxLen = Constants.TEXT_LENGTH
        if (!awayMsg.contains(mandatoryMsg)) {
            this.awayMessage = Helpers.appendGuaranteeLength(awayMsg, mandatoryMsg, maxLen).trim()
        }
    }

    // Static finders
    // --------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static HashSet<Phone> getPhonesForRecords(List<Record> recs) {
        if (!recs) { return new HashSet<Phone>() }
        List<Phone> cPhones = Contact.createCriteria().list {
                projections { property("phone") }
                "in"("record", recs)
                eq("isDeleted", false)
            }, tPhones = ContactTag.createCriteria().list {
                projections { property("phone") }
                "in"("record", recs)
                eq("isDeleted", false)
            },
            phones = cPhones + tPhones,
            // phones from contacts that are shared with me
            sPhones = SharedContact.createCriteria().list {
                projections { property("sharedWith") }
                contact {
                    "in"("record", recs)
                    eq("isDeleted", false)
                }
                if (phones) {
                    "in"("sharedBy", phones)
                }
                else { eq("sharedBy", null) }
            }
        new HashSet<Phone>(phones + sPhones)
    }

    // Status
    // ------

    Phone deactivate() {
        this.numberAsString = null
        this.apiId = null
        this
    }

    // Ownership
    // ---------

    Result<PhoneOwnership> transferTo(Long id, PhoneOwnershipType type) {
        PhoneOwnership own = this.owner
        Phone otherPhone = (type == PhoneOwnershipType.INDIVIDUAL) ?
            Staff.get(id)?.phoneWithAnyStatus : Team.get(id)?.phoneWithAnyStatus
        // if other phone is present, copy this owner over
        if (otherPhone?.owner) {
            PhoneOwnership otherOwn = otherPhone.owner
            otherOwn.type = own.type
            otherOwn.ownerId = own.ownerId
            if (!otherOwn.save()) {
                return resultFactory.failWithValidationErrors(otherOwn.errors)
            }
        }
        // then associate this phone with new owner
        own.type = type
        own.ownerId = id
        if (own.save()) {
            resultFactory.success(own)
        }
        else { resultFactory.failWithValidationErrors(own.errors) }
    }

    // Tags
    // ----

    Result<ContactTag> createTag(Map params) {
        ContactTag tag = new ContactTag()
        tag.phone = this
        tag.name = params.name
        if (params.hexColor) tag.hexColor = params.hexColor
        if (params.isDeleted != null) tag.isDeleted = params.isDeleted

        // need to validate to initialize empty record
        if (tag.validate()) {
            // don't need null-check because withDefault uses the phone's language
            // if the provided value is null
            tag.language = Helpers.withDefault(
                Helpers.convertEnum(VoiceLanguage, params.language),
                this.language
            )
            if (tag.save()) {
                return resultFactory.success(tag, ResultStatus.CREATED)
            }
        }
        resultFactory.failWithValidationErrors(tag.errors)
    }

    // Contacts
    // --------

    Result<Contact> createContact(Map params=[:], Collection<String> numbers=[]) {
        Contact contact = new Contact([:])
        contact.phone = this
        if (params.name) contact.name = params.name
        if (params.note) contact.note = params.note
        if (params.status) contact.status = Helpers.convertEnum(ContactStatus, params.status)
        if (params.isDeleted != null) contact.isDeleted = params.isDeleted
        // if language is provided but record is not initialized yet, need to do so in order
        // to set the language on the record object
        VoiceLanguage lang = Helpers.convertEnum(VoiceLanguage, params.language)
        if (lang && !contact.record) {
            contact.record = new Record(language: Helpers.withDefault(lang, this.language))
            if (!contact.record.save()) {
                return resultFactory.failWithValidationErrors(contact.record.errors)
            }
        }
        // need to save contact before adding numbers so that the contact domain is assigned an
        // ID to be associated with the ContactNumbers to avoid a TransientObjectException
        if (contact.save()) {
            Collection<String> toBeAdded = numbers.unique()
            int numbersLen = toBeAdded.size()
            for (int i = 0; i < numbersLen; i++) {
                String num = toBeAdded[i]
                Result res = contact.mergeNumber(num, [preference:i])
                if (!res.success) {
                    return res
                }
            }
            return resultFactory.success(contact, ResultStatus.CREATED)
        }
        resultFactory.failWithValidationErrors(contact.errors)
    }

    // Sharing
    // -------

    boolean canShare(Phone sWith) {
        if (!sWith) { return false }
        Collection<Team> myTeams = Team.listForStaffs(this.owner.all),
            sharedWithTeams = Team.listForStaffs(sWith.owner.all)
        HashSet<Team> allowedTeams = new HashSet<>(myTeams)
        sharedWithTeams.any { it in allowedTeams }
    }
    Result<SharedContact> share(Contact c1, Phone sWith, SharePermission perm) {
        if (c1?.phone?.id != this.id) {
            return resultFactory.failWithCodeAndStatus("phone.contactNotMine",
                ResultStatus.BAD_REQUEST, [c1?.name])
        }
        if (!canShare(sWith)) {
            return resultFactory.failWithCodeAndStatus("phone.share.cannotShare",
                ResultStatus.FORBIDDEN, [sWith?.name])
        }
        //check to see that there isn't already an active shared contact
        SharedContact sc = SharedContact.listForContactAndSharedWith(c1, sWith, [max:1])[0]
        if (sc) {
            sc.startSharing(c1.status, perm)
        }
        else {
            sc = new SharedContact(contact:c1, sharedBy:this, sharedWith:sWith, permission:perm)
        }
        if (sc.save()) {
            resultFactory.success(sc)
        }
        else { resultFactory.failWithValidationErrors(sc.errors) }
    }
    Result<Void> stopShare(Phone sWith) {
        List<SharedContact> shareds = SharedContact.listForSharedByAndSharedWith(this, sWith)
        shareds.each { SharedContact sc -> sc.stopSharing() }
        resultFactory.success()
    }
    Result<Void> stopShare(Contact c1, Phone sWith) {
        SharedContact sc1 = SharedContact.listForContactAndSharedWith(c1, sWith, [max:1])[0]
        if (sc1) {
            sc1.stopSharing()
        }
        resultFactory.success()
    }
    Result<Void> stopShare(Contact contact) {
        // Hibernate proxying magic sometimes results in either contact,phone or this phone being
        // a proxy and therefore not equal to each other when they should be. We get around this
        // problem by comparing the ids of the two objects to ascertain identity
        if (contact?.phone?.id != this.id) {
            return resultFactory.failWithCodeAndStatus("phone.contactNotMine",
                ResultStatus.BAD_REQUEST, [contact?.getNameOrNumber()])
        }
        List<SharedContact> shareds = SharedContact.listForContact(contact)
        shareds?.each { SharedContact sc -> sc.stopSharing() }
        resultFactory.success()
    }

    // Property Access
    // ---------------

    String getName() {
        this.owner.name
    }
    boolean getIsActive() {
        this.getNumber().validate()
    }
    void setNumber(BasePhoneNumber pNum) {
        this.numberAsString = pNum?.number
    }
    PhoneNumber getNumber() {
        new PhoneNumber(number:this.numberAsString)
    }

    int countTags() {
        ContactTag.countByPhoneAndIsDeleted(this, false)
    }
    List<ContactTag> getTags(Map params=[:]) {
        ContactTag.findAllByPhoneAndIsDeleted(this, false, params + [sort: "name", order: "desc"])
    }
    //Optional specify 'status' corresponding to valid contact statuses
    int countContacts(Map params=[:]) {
        if (params == null) {
            return 0
        }
        Contact.countForPhoneAndStatuses(this,
            Helpers.toEnumList(ContactStatus, params.statuses, Constants.CONTACT_ACTIVE_STATUSES))
    }
    //Optional specify 'status' corresponding to valid contact statuses
    List<Contactable> getContacts(Map params=[:]) {
        if (params == null) {
            return []
        }
        Collection<ContactStatus> statusEnums =
            Helpers.toEnumList(ContactStatus, params.statuses, Constants.CONTACT_ACTIVE_STATUSES)
        //get contacts, both mine and those shared with me
        List<Contact> contactList = Contact.listForPhoneAndStatuses(this, statusEnums, params)
        replaceContactsWithShared(contactList)
    }
    int countContacts(String query) {
        if (query == null) {
            return 0
        }
        Contact.countForPhoneAndSearch(this, Helpers.toQuery(query))
    }
    List<Contactable> getContacts(String query, Map params=[:]) {
        if (query == null) {
            return []
        }
        //get contacts, both mine and those shared with me
        replaceContactsWithShared(Contact.listForPhoneAndSearch(this, Helpers.toQuery(query), params))
    }
    int countSharedWithMe() {
        SharedContact.countSharedWithMe(this)
    }
    List<SharedContact> getSharedWithMe(Map params=[:]) {
        SharedContact.listSharedWithMe(this, params)
    }
    int countSharedByMe() {
        SharedContact.countSharedByMe(this)
    }
    List<Contact> getSharedByMe(Map params=[:]) {
        SharedContact.listSharedByMe(this, params)
    }
    // some contacts returned may not be mine and may instead have a SharedContact
    // that is shared with me. For these, we want to do an in-place replacement
    // of the contact that doesn't belong to me with the SharedContact
    protected List<Contactable> replaceContactsWithShared(List<Contact> contacts) {
        //identify those contacts that are shared with me and their index
        HashSet<Long> notMyContactIds = new HashSet<>()
        contacts.each { Contact contact ->
            if (contact.phone != this) { notMyContactIds << contact.id }
        }
        //if all contacts are mine, we can return
        if (notMyContactIds.isEmpty()) { return contacts }
        //retrieve the corresponding SharedContact instance
        Map<Long,SharedContact> contactIdToSharedContact = (SharedContact.createCriteria()
            .list {
                if (notMyContactIds) {
                    "in"("contact.id", notMyContactIds)
                }
                else { eq("contact.id", null) }
                // ensure that we only fetch SharedContacts that belong to this phone
                eq("sharedWith", this)
                // Ensure that SharedContact is not expired. This might become a problem
                // when a contact has two SharedContacts, one active and one expired.
                // The contact will show up in the contacts list and when we find the shared
                // contacts from the contact ids, we want to only get the SharedContact
                // that is active and exclude the one that is expired
                or {
                    isNull("dateExpired") //not expired if null
                    gt("dateExpired", DateTime.now())
                }
            } as List<SharedContact>)
            .collectEntries { [(it.contact.id):it] }
        //merge the found shared contacts into a consensus list of contactables
        List<Contactable> contactables = []
        contacts.each { Contact contact ->
            if (notMyContactIds.contains(contact.id)) {
                if (contactIdToSharedContact.containsKey(contact.id)) {
                    contactables << contactIdToSharedContact[contact.id]
                }
                // if shared contact not found, silently ignore this contact
            }
            else { contactables << contact }
        }
        contactables
    }

    int countAnnouncements() {
        FeaturedAnnouncement.countForPhone(this)
    }
    List<FeaturedAnnouncement> getAnnouncements(Map params=[:]) {
        FeaturedAnnouncement.listForPhone(this, params)
    }
    int countSessions() {
        IncomingSession.countByPhone(this)
    }
    List<IncomingSession> getSessions(Map params=[:]) {
        IncomingSession.findAllByPhone(this, params)
    }
    int countCallSubscribedSessions() {
        IncomingSession.countByPhoneAndIsSubscribedToCall(this, true)
    }
    List<IncomingSession> getCallSubscribedSessions(Map params=[:]) {
        IncomingSession.findAllByPhoneAndIsSubscribedToCall(this, true, params)
    }
    int countTextSubscribedSessions() {
        IncomingSession.countByPhoneAndIsSubscribedToText(this, true)
    }
    List<IncomingSession> getTextSubscribedSessions(Map params=[:]) {
        IncomingSession.findAllByPhoneAndIsSubscribedToText(this, true, params)
    }

    Result<PhoneOwnership> updateOwner(Team t1) {
        PhoneOwnership own = PhoneOwnership.findByOwnerIdAndType(t1.id,
                PhoneOwnershipType.GROUP) ?:
            new PhoneOwnership(ownerId:t1.id, type:PhoneOwnershipType.GROUP)
        updateOwner(own)
    }
    Result<PhoneOwnership> updateOwner(Staff s1) {
        PhoneOwnership own = PhoneOwnership.findByOwnerIdAndType(s1.id,
                PhoneOwnershipType.INDIVIDUAL) ?:
            new PhoneOwnership(ownerId:s1.id, type:PhoneOwnershipType.INDIVIDUAL)
        updateOwner(own)
    }
    protected Result<PhoneOwnership> updateOwner(PhoneOwnership own) {
        own.phone = this
        this.owner = own
        if (own.save()) {
            resultFactory.success(own)
        }
        else { resultFactory.failWithValidationErrors(own.errors) }
    }

    // Outgoing
    // --------

    ResultGroup<RecordItem> sendMessage(OutgoingMessage msg, Staff staff = null,
        boolean skipOwnerCheck = false) {
        if (!this.isActive) {
            resultFactory.failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND).toGroup()
        }
        else if (!msg.validateSetPhone(this)) { // validate msg
            resultFactory.failWithValidationErrors(msg.errors).toGroup()
        }
        else if (!skipOwnerCheck && !this.owner.all.any { Staff s1 -> s1.id == staff.id }) { // validate staff
            resultFactory.failWithCodeAndStatus("phone.notOwner", ResultStatus.FORBIDDEN).toGroup()
        }
        else { phoneService.sendMessage(this, msg, staff) }
    }
    // start bridge call, confirmed (if staff picks up) by contact
    Result<RecordCall> startBridgeCall(Contactable c1, Staff staff) {
        if (!this.isActive) {
            resultFactory.failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND)
        }
        else if ((c1 instanceof Contact || c1 instanceof SharedContact) && !c1.validate()) {
            resultFactory.failWithValidationErrors(c1.errors)
        }
        else if (c1 instanceof Contact && c1.phone?.id != this.id) {
            resultFactory.failWithCodeAndStatus("phone.startBridgeCall.forbidden", ResultStatus.FORBIDDEN)
        }
        else if (c1 instanceof SharedContact && !(c1.canModify && c1.sharedWith?.id == this.id)) {
            resultFactory.failWithCodeAndStatus("phone.startBridgeCall.forbidden", ResultStatus.FORBIDDEN)
        }
        else if (!this.owner.all.any { Staff s1 -> s1.id == staff.id }) { // validate staff
            resultFactory.failWithCodeAndStatus("phone.notOwner", ResultStatus.FORBIDDEN)
        }
        else if (!staff.personalPhoneAsString) {
            resultFactory.failWithCodeAndStatus("phone.startBridgeCall.noPersonalNumber",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        else {
            phoneService.startBridgeCall(this, c1, staff)
        }
    }
    Result<FeaturedAnnouncement> sendAnnouncement(String message,
        DateTime expiresAt, Staff staff) {
        if (!this.isActive) {
            return resultFactory.failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND)
        }
        // validate expiration
        if (!expiresAt || expiresAt.isBeforeNow()) {
            return resultFactory.failWithCodeAndStatus("phone.sendAnnouncement.expiresInPast",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        // validate staff
        if (!this.owner.all.contains(staff)) {
            return resultFactory.failWithCodeAndStatus("phone.notOwner", ResultStatus.FORBIDDEN)
        }
        // collect relevant classes
        List<IncomingSession> textSubs = IncomingSession.findAllByPhoneAndIsSubscribedToText(this, true),
            callSubs = IncomingSession.findAllByPhoneAndIsSubscribedToCall(this, true)
        String identifier = this.name
        // send announcements
        Map<String, Result<TempRecordReceipt>> textRes = phoneService
            .sendTextAnnouncement(this, message, identifier, textSubs, staff)
        Map<String, Result<TempRecordReceipt>> callRes = phoneService
            .startCallAnnouncement(this, message, identifier, callSubs, staff)
        // build announcements class
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:this,
            expiresAt:expiresAt, message:message)
        //mark as to-be-saved to avoid TransientObjectExceptions
        if (!announce.save()) {
            resultFactory.failWithValidationErrors(announce.errors)
        }
        // collect sessions that we successfully reached
        Collection<IncomingSession> successTexts = textSubs.findAll {
            textRes[it.numberAsString]?.success
        }, successCalls = callSubs.findAll {
            callRes[it.numberAsString]?.success
        }
        // add sessions to announcement as receipts
        ResultGroup<AnnouncementReceipt> textResGroup = announce.addToReceipts(RecordItemType.TEXT, successTexts),
            callResGroup = announce.addToReceipts(RecordItemType.CALL, successCalls)
        textResGroup.logFail("Phone.sendAnnouncement: add text announce receipts")
        callResGroup.logFail("Phone.sendAnnouncement: add call announce receipts")
        // don't use announce.numReceipts here because the dynamic finder
        // will flush the session
        boolean noSubscribers = (!textSubs && !callSubs),
            anySuccessWithSubscribers = (textResGroup.anySuccesses || callResGroup.anySuccesses) && (textSubs || callSubs)
        if (noSubscribers || anySuccessWithSubscribers) {
            if (announce.save()) {
                resultFactory.success(announce)
            }
            else { resultFactory.failWithValidationErrors(announce.errors) }
        }
        // return error if all subscribers failed to receive announcement
        else {
            resultFactory.failWithResultsAndStatus(textRes.values() + callRes.values(),
                ResultStatus.INTERNAL_SERVER_ERROR)
        }
    }

    // Incoming
    // --------

    Result<Closure> receiveText(IncomingText text, IncomingSession session) {
        if (!text.validate()) { //validate text
            resultFactory.failWithValidationErrors(text.errors)
        }
        else if (session.phone?.id != this.id) { //validate session
            resultFactory.failWithCodeAndStatus("phone.receive.notMine", ResultStatus.FORBIDDEN)
        }
        else if (this.getAnnouncements()) {
            switch (text.message) {
                case Constants.TEXT_SEE_ANNOUNCEMENTS:
                    Collection<FeaturedAnnouncement> announces = this.getAnnouncements()
                    announces.each { FeaturedAnnouncement announce ->
                        announce
                            .addToReceipts(RecordItemType.TEXT, session)
                            .logFail("Phone.receiveText: add announce receipt")
                    }
                    twimlBuilder.build(TextResponse.SEE_ANNOUNCEMENTS,
                        [announcements:announces])
                    break
                case Constants.TEXT_TOGGLE_SUBSCRIBE:
                    if (session.isSubscribedToText) {
                        session.isSubscribedToText = false
                        twimlBuilder.build(TextResponse.UNSUBSCRIBED)
                    }
                    else {
                        session.isSubscribedToText = true
                        twimlBuilder.build(TextResponse.SUBSCRIBED)
                    }
                    break
                default:
                    phoneService.relayText(this, text, session)
            }
        }
        else { phoneService.relayText(this, text, session) }
    }
    Result<Closure> receiveCall(String apiId, String digits, IncomingSession session) {
        if (session.phone != this) { //validate session
            resultFactory.failWithCodeAndStatus("phone.receive.notMine", ResultStatus.FORBIDDEN)
        }
        //if staff member is calling from personal phone to TextUp phone
        else if (this.owner.all.any { it.personalPhoneAsString == session.numberAsString }) {
            Staff staff = this.owner.all.find {
                it.personalPhoneAsString == session.numberAsString
            }
            phoneService.handleSelfCall(this, apiId, digits, staff)
        }
        else if (this.getAnnouncements()) {
            phoneService.handleAnnouncementCall(this, apiId, digits, session)
        }
        else {
            phoneService.relayCall(this, apiId, session)
        }
    }
    Result<Closure> screenIncomingCall(IncomingSession session) {
        if (session.phone?.id != this?.id) { //validate session
            resultFactory.failWithCodeAndStatus("phone.receive.notMine", ResultStatus.FORBIDDEN)
        }
        else {
            phoneService.screenIncomingCall(this, session)
        }
    }
    Result<Closure> tryStartVoicemail(PhoneNumber fromNum, PhoneNumber toNum, ReceiptStatus status) {
        if (status == ReceiptStatus.SUCCESS) { // call already connected so no voicemail
            twimlBuilder.noResponse()
        }
        else {
            twimlBuilder.build(CallResponse.CHECK_IF_VOICEMAIL,
                [
                    voice: this.voice,
                    awayMessage:this.awayMessage,
                    // no-op for Record Twiml verb to call because recording might not be ready
                    linkParams:[handle:CallResponse.END_CALL],
                    // need to population From and To parameters to help in finding
                    // phone and session in the recording status hook
                    callbackParams:[handle:CallResponse.VOICEMAIL_DONE,
                        From:fromNum.e164PhoneNumber, To:toNum.e164PhoneNumber]
                ])
        }
    }
    Result<Closure> completeVoicemail(String callId, String recordingId, String voicemailUrl,
        Integer voicemailDuration) {
        // move the encrypted voicemail to s3 and delete recording at Twilio
        phoneService.moveVoicemail(callId, recordingId, voicemailUrl)
            .logFail("Phone.moveVoicemail: call: ${callId}, recording: ${recordingId}")
            .then({
                phoneService
                    .storeVoicemail(callId, voicemailDuration)
                    .logFail("Phone.storeVoicemail: call: ${callId}, recording: ${recordingId}")
                twimlBuilder.build(CallResponse.VOICEMAIL_DONE)
            })
    }
    Result<Closure> finishBridgeCall(Contact c1) {
        twimlBuilder.build(CallResponse.FINISH_BRIDGE, [contact:c1])
    }
    Result<Closure> completeCallAnnouncement(String digits, String message,
        String identifier, IncomingSession session) {
        if (digits == Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE) {
            session.isSubscribedToCall = false
            twimlBuilder.build(CallResponse.UNSUBSCRIBED)
        }
        else {
            twimlBuilder.build(CallResponse.ANNOUNCEMENT_AND_DIGITS,
                [message:message, identifier:identifier])
        }
    }
}
