package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.hibernate.FlushMode
import org.hibernate.Session
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.types.CallResponse
import org.textup.types.ContactStatus
import org.textup.types.PhoneOwnershipType
import org.textup.types.RecordItemType
import org.textup.types.SharePermission
import org.textup.types.TextResponse
import org.textup.validator.BasePhoneNumber
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingText
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@EqualsAndHashCode(excludes="owner")
@RestApiObject(name="Staff", description="A TextUp phone.")
class Phone {

    ResultFactory resultFactory
    PhoneService phoneService
    TwimlBuilder twimlBuilder

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

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "number",
            description  = "Phone number of this TextUp phone.",
            allowedType  = "String"),
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
            useForCreation = false)
    ])
    static transients = ["number", "resultFactory", "phoneService", "twimlBuilder"]
    static constraints = {
        apiId blank:true, nullable:true, unique:true
        numberAsString blank:true, nullable:true, validator:{ String num, Phone obj ->
            //phone number must be unique for phones
            if (num && obj.existsWithSameNumber(num)) {
                ["duplicate"]
            }
            else if (!(num?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
        awayMessage blank:false, size:1..(Constants.TEXT_LENGTH)
    }

    /*
	Has many:
		Contact
		ContactTag
	*/

    // Validator
    // ---------

    protected boolean existsWithSameNumber(String num) {
        boolean hasDuplicate = false
        Phone.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                PhoneNumber pNum = new PhoneNumber(number:num)
                Phone ph = Phone.findByNumberAsString(pNum.number)
                if (ph && ph.id != this.id) { hasDuplicate = true }
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        hasDuplicate
    }

    // Ownership
    // ---------

    Result<PhoneOwnership> transferTo(Long id, PhoneOwnershipType type) {
        PhoneOwnership own = this.owner
        Phone otherPhone = (type == PhoneOwnershipType.INDIVIDUAL) ?
            Staff.get(id)?.phone : Team.get(id)?.phone
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
        tag.with {
            phone = this
            name = params.name
            if (params.hexColor) hexColor = params.hexColor
        }
        if (tag.save()) {
            resultFactory.success(tag)
        }
        else { resultFactory.failWithValidationErrors(tag.errors) }
    }

    // Contacts
    // --------

    Result<Contact> createContact(Map params=[:], Collection<String> numbers=[]) {
        Contact contact = new Contact([:])
        contact.properties = params
        contact.phone = this
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
            resultFactory.success(contact)
        }
        else { resultFactory.failWithValidationErrors(contact.errors) }
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
        if (c1?.phone != this) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "phone.contactNotMine", [c1?.name])
        }
        if (!canShare(sWith)) {
            return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                "phone.share.cannotShare", [sWith?.name])
        }
        //check to see that there isn't already an active shared contact
        SharedContact sc = SharedContact
            .listForContactAndSharedWith(c1, sWith, [max:1])[0]
        if (sc) {
            sc.startSharing(perm)
        }
        else {
            sc = new SharedContact(contact:c1, sharedBy:this, sharedWith:sWith,
                permission:perm)
        }
        if (sc.save()) {
            resultFactory.success(sc)
        }
        else { resultFactory.failWithValidationErrors(sc.errors) }
    }
    Result stopShare(Phone sWith) {
        List<SharedContact> shareds = SharedContact
            .listForSharedByAndSharedWith(this, sWith)
        shareds.each { SharedContact sc -> sc.stopSharing() }
        resultFactory.success()
    }
    Result stopShare(Contact c1, Phone sWith) {
        SharedContact sc1 = SharedContact
            .listForContactAndSharedWith(c1, sWith, [max:1])[0]
        if (sc1) {
            sc1.stopSharing()
        }
        resultFactory.success()
    }
    Result stopShare(Contact contact) {
        if (contact?.phone != this) {
            return resultFactory.failWithMessage("phone.contactNotMine", [contact?.name])
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
        ContactTag.findAllByPhoneAndIsDeleted(this, false,
            params + [sort: "name", order: "desc"])
    }
    //Optional specify 'status' corresponding to valid contact statuses
    int countContacts(Map params=[:]) {
        Contact.countForPhoneAndStatuses(this,
            Helpers.<ContactStatus>toEnumList(ContactStatus, params.statuses))
    }
    //Optional specify 'status' corresponding to valid contact statuses
    List<Contactable> getContacts(Map params=[:]) {
        Collection<ContactStatus> statusEnums =
            Helpers.<ContactStatus>toEnumList(ContactStatus, params.statuses)
        //get contacts, both mine and those shared with me
        List<Contact> contacts = Contact.listForPhoneAndStatuses(this, statusEnums, params)
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
    int countContacts(String query) {
        Contact.countByPhoneAndNameIlike(this, Helpers.toQuery(query))
    }
    List<Contact> getContacts(String query, Map params=[:]) {
        Contact.findAllByPhoneAndNameIlike(this, Helpers.toQuery(query), params)
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
    List<Staff> getAvailableNow() {
        List<Staff> availableNow = []
        this.owner.all.each { Staff s1 ->
            if (s1.isAvailableNow()) {
                availableNow << s1
            }
        }
        availableNow
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

    ResultList<RecordText> sendText(OutgoingText text, Staff staff) {
        if (!this.isActive) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                'phone.isInactive')
        }
        // validate text
        if (!text.validateSetPhone(this)) {
            return new ResultList(resultFactory.failWithValidationErrors(text.errors))
        }
        // validate staff
        else if (!this.owner.all.contains(staff)) {
            return new ResultList(resultFactory.failWithMessageAndStatus(FORBIDDEN,
                'phone.notOwner'))
        }
        else { phoneService.sendText(this, text, staff) }
    }
    // start bridge call, confirmed (if staff picks up) by contact
    ResultList<RecordCall> startBridgeCall(Contactable c1, Staff staff) {
        if (!this.isActive) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                'phone.isInactive')
        }
        ResultList<RecordCall> resList = new ResultList()
        if ((c1 instanceof Contact || c1 instanceof SharedContact) && !c1.validate()) {
            return resList << resultFactory.failWithValidationErrors(c1.errors)
        }
        else if (c1 instanceof Contact && c1.phone != this) {
            return resList << resultFactory.failWithMessageAndStatus(FORBIDDEN,
                'phone.startBridgeCall.forbidden')
        }
        else if (c1 instanceof SharedContact && !(c1.canModify && c1.sharedWith == this)) {
            return resList << resultFactory.failWithMessageAndStatus(FORBIDDEN,
                'phone.startBridgeCall.forbidden')
        }
        else if (!this.owner.all.contains(staff)) { // validate staff
            return resList << resultFactory.failWithMessageAndStatus(FORBIDDEN,
                'phone.notOwner')
        }
        else if (!staff.personalPhoneAsString) {
            return resList << resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                'phone.startBridgeCall.noPersonalNumber')
        }
        resList << phoneService.startBridgeCall(this, c1, staff)
    }
    Result<FeaturedAnnouncement> sendAnnouncement(String message,
        DateTime expiresAt, Staff staff) {
        if (!this.isActive) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                'phone.isInactive')
        }
        // validate expiration
        if (!expiresAt || expiresAt.isBeforeNow()) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "phone.sendAnnouncement.expiresInPast")
        }
        // validate staff
        if (!this.owner.all.contains(staff)) {
            return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                "phone.notOwner")
        }
        // collect relevant classes
        List<IncomingSession> textSubs =
                IncomingSession.findAllByPhoneAndIsSubscribedToText(this, true),
            callSubs = IncomingSession.findAllByPhoneAndIsSubscribedToCall(this, true)
        String identifier = this.name
        // send announcements
        ResultMap<TempRecordReceipt> textRes = phoneService.sendTextAnnouncement(this,
            message, identifier, textSubs, staff).logFail("Phone.sendAnnouncement: text")
        ResultMap<TempRecordReceipt> callRes = phoneService.startCallAnnouncement(this,
            message, identifier, callSubs, staff).logFail("Phone.sendAnnouncement: call")
        // build announcements class
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:this,
            expiresAt:expiresAt, message:message)
        //mark as to-be-saved to avoid TransientObjectExceptions
        if (!announce.save()) {
            resultFactory.failWithValidationErrors(announce.errors)
        }
        // collect sessions that we successfully reached
        Collection<IncomingSession> successTexts = textSubs.findAll {
            textRes.isSuccess(it.numberAsString)
        }, successCalls = callSubs.findAll {
            callRes.isSuccess(it.numberAsString)
        }
        // add sessions to announcement as receipts
        ResultList<AnnouncementReceipt> textResList = announce.addToReceipts(
                RecordItemType.TEXT, successTexts),
            callResList = announce.addToReceipts(RecordItemType.CALL, successCalls)
        textResList.logFail("Phone.sendAnnouncement: add text announce receipts")
        callResList.logFail("Phone.sendAnnouncement: add call announce receipts")
        // don't use announce.numReceipts here because the dynamic finder
        // will flush the session
        boolean noSubscribers = (!textSubs && !callSubs),
            anySuccessWithSubscribers = (textResList.isAnySuccess ||
                callResList.isAnySuccess) && (textSubs || callSubs)
        if (noSubscribers || anySuccessWithSubscribers) {
            if (announce.save()) {
                resultFactory.success(announce)
            }
            else { resultFactory.failWithValidationErrors(announce.errors) }
        }
        // return error if all subscribers failed to receive announcement
        else {
            resultFactory.failWithResultsAndStatus(INTERNAL_SERVER_ERROR,
               textRes.getResults() + callRes.getResults())
        }
    }

    // Incoming
    // --------

    Result<Closure> receiveText(IncomingText text, IncomingSession session) {
        if (!text.validate()) { //validate text
            resultFactory.failWithValidationErrors(text.errors)
        }
        else if (session.phone != this) { //validate session
            resultFactory.failWithMessageAndStatus(FORBIDDEN, 'phone.receive.notMine')
        }
        else if (this.getAnnouncements()) {
            switch (text.message) {
                case Constants.TEXT_SEE_ANNOUNCEMENTS:
                    Collection<FeaturedAnnouncement> announces = this.getAnnouncements()
                    announces.each { FeaturedAnnouncement announce ->
                        announce.addToReceipts(RecordItemType.TEXT, session)
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
            resultFactory.failWithMessageAndStatus(FORBIDDEN, 'phone.receive.notMine')
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
    Result<Closure> receiveVoicemail(String apiId, Integer voicemailDuration,
        IncomingSession session) {
        if (voicemailDuration) {
            // move the encrypted voicemail to s3 and delete recording at Twilio
            phoneService.moveVoicemail(apiId)
                .logFail("Phone.moveVoicemail")
                .then({ ->
                    phoneService.storeVoicemail(apiId, voicemailDuration)
                        .logFail("Phone.storeVoicemail")
                    twimlBuilder.noResponse()
                }) as Result
        }
        else {
            twimlBuilder.build(CallResponse.VOICEMAIL,
                [linkParams:[handle:CallResponse.VOICEMAIL]])
        }
    }
    // staff must pick up and press any number to start the bridge
    Result<Closure> confirmBridgeCall(Contact c1) {
        twimlBuilder.build(CallResponse.CONFIRM_BRIDGE, [contact:c1,
            linkParams:[contactId:c1?.contactId, handle:CallResponse.FINISH_BRIDGE]])
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
