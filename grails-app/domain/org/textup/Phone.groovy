package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.joda.time.DateTime
import static org.springframework.http.HttpStatus.*

@EqualsAndHashCode
class Phone {

    def resultFactory
    def phoneService
    def twimlBuilder
    def authService

    //unique id assigned to this phone's number
    String apiId
	String numberAsString
    PhoneOwnership owner
    String awayMessage = Constants.DEFAULT_AWAY_MESSAGE

    static transients = ["number"]
    static constraints = {
        apiId blank:true, nullable:true, unique:true
        number shared: 'phoneNumber', validator:{ pNum, obj ->
            //phone number must be unique for phones
            if (pNum && obj.existsWithSameNumber(pNum.number)) {
                ["duplicate"]
            }
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

    private boolean existsWithSameNumber(String num) {
        boolean hasDuplicate = false
        Phone.withNewSession { session ->
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

    Result<Contact> createContact(Map params=[:], List<String> numbers=[]) {
        Contact contact = new Contact([:])
        contact.properties = params
        contact.phone = this
        if (contact.save()) {
            //merge number has a dynamic finder that will flush
            Result prematureReturn = null
            Phone.withNewSession { session ->
                session.flushMode = FlushMode.MANUAL
                try {
                    int numbersLen = numbers.size()
                    for (int i = 0; i < numbersLen; i++) {
                        String num = numbers[i]
                        Result res = contact.mergeNumber(num, [preference:i])
                        if (!res.success) {
                            prematureReturn = resultFactory.failWithValidationErrors(res.payload)
                            return //return from withNewSession closure
                        }
                    }
                }
                finally { session.flushMode = FlushMode.AUTO }
            }
            prematureReturn ?: resultFactory.success(contact)
        }
        else { resultFactory.failWithValidationErrors(contact.errors) }
    }

    // Sharing
    // -------

    boolean canShare(Phone sWith) {
        if (!sWith) { return false }
        List<Team> myTeams = Team.forStaffs(this.owner.all).list(),
            sharedWithTeams = Team.forStaffs(sWith.owner.all).list()
        HashSet<Team> allowedTeams = new HashSet<>(myTeams)
        sharedWithTeams.any { it in allowedTeams }
    }
    Result<SharedContact> share(Contact c1, Phone sWith, SharePermission perm) {
        if (contact?.phone != this) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "phone.contactNotMine", [c1?.name])
        }
        if (!canShare(sWith)) {
            return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                "phone.share.cannotShare", [sWith?.name])
        }
        //check to see that there isn't already an active shared contact
        SharedContact sc = SharedContact
            .forContactAndSharedWith(c1, sWith).list(max:1)[0]
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
    Result<List<SharedContact>> stopShare(Phone sWith) {
        List<SharedContact> shareds = SharedContact
            .forSharedByAndSharedWith(this, sWith).list()
        shareds.each { SharedContact sc -> sc.stopSharing() }
        resultFactory.success(shareds)
    }
    Result<SharedContact> stopShare(Contact c1, Phone sWith) {
        SharedContact sc1 = SharedContact
            .forContactAndSharedWith(c1, sWith).list(max:1)[0]
        if (sc1) {
            sc1.stopSharing()
            resultFactory.success(sc1)
        }
        else { resultFactory.failWithMessageAndStatus(NOT_FOUND,
            "phone.stopShare.notShared", [c1?.name]) }
    }
    Result<List<SharedContact>> stopShare(Contact contact) {
        if (contact?.phone != this) {
            return resultFactory.failWithMessage("phone.contactNotMine", [contact?.name])
        }
        List<SharedContact> shareds = SharedContact.forContact(contact).list()
        if (shareds) {
            shareds.each { SharedContact sc -> sc.stopSharing() }
            resultFactory.success(shareds)
        }
        else {
            resultFactory.failWithMessage("phone.stopShare.notShared", [contact?.name])
        }
    }

    // Property Access
    // ---------------

    String getName() {
        this.owner.name
    }
    void setNumber(PhoneNumber pNum) {
        this.numberAsString = pNum?.number
    }
    PhoneNumber getNumber() {
        new PhoneNumber(number:this.numberAsString)
    }
    int countTags() {
        ContactTag.countByPhone(this)
    }
    List<ContactTag> getTags(Map params=[:]) {
        ContactTag.findAllByPhone(this, params + [sort: "name", order: "desc"])
    }
    //Optional specify 'status' corresponding to valid contact statuses
    int countContacts(Map params=[:]) {
        Contact.forPhoneAndStatuses(this, Helpers.toEnumList(params.statuses)).count() ?: 0
    }
    //Optional specify 'status' corresponding to valid contact statuses
    List<Contactable> getContacts(Map params=[:]) {
        Collection<ContactStatus> statusEnums =
            Helpers.toEnumList(ContactStatus, params.statuses)
        //get contacts, both mine and those shared with me
        List<Contact> contacts = Contact.forPhoneAndStatuses(this, statusEnums).list(params)
        //identify those contacts that are shared with me and their index
        HashSet<Long> notMyContactIds = new HashSet<>()
        contacts.each { Contact contact ->
            if (contact.phone != this) { notMyContactIds << contact.id }
        }
        //if all contacts are mine, we can return
        if (notMyContactIdToIndex.isEmpty()) { return contacts }
        //retrieve the corresponding SharedContact instance
        Map<Long,SharedContact> contactIdToSharedContact = SharedContact.createCriteria()
            .list {
                contact {
                    if (notMyContactIds) { "in"("id", notMyContactIds) }
                    else { eq("id", null) }
                }
            }
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
        SharedContact.sharedWithMe(this).count()
    }
    List<SharedContact> getSharedWithMe(Map params=[:]) {
        SharedContact.sharedWithMe(this).list(params) ?: []
    }
    int countSharedByMe() {
        SharedContact.sharedByMe(this).count()
    }
    List<Contact> getSharedByMe(Map params=[:]) {
        SharedContact.sharedByMe(this).list(params) ?: []
    }
    List<FeaturedAnnouncement> getAnnouncements() {
        FeaturedAnnouncement.forPhone(this).list()
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

    // Outgoing
    // --------

    ResultList<RecordText> sendText(OutgoingText text, Staff staff) {
        // validate text
        if (!text.validate(this)) {
            return new ResultList(resultFactory.failWithValidationErrors(text.errors))
        }
        // validate staff
        else if (!this.owner.all.contains(staff)) {
            return new ResultList(resultFactory.failWithMessageAndStatus(FORBIDDEN,
                'phone.notOwner'))
        }
        else { phoneService.sendText(phone, text, staff) }
    }
    // start bridge call, confirmed (if staff picks up) by contact
    ResultList<RecordCall> startBridgeCall(Contactable c1, Staff staff) {
        if (!c1.validate()) {
            resultFactory.failWithValidationErrors(c1.errors)
        }
        else if (c1.instanceOf(Contact) || c1.phone != p1) {
            resultFactory.failWithMessageAndStatus(FORBIDDEN, 'phone.startBridgeCall.forbidden')
        }
        else if (c1.instanceOf(SharedContact) && c1.canModify) {
            resultFactory.failWithMessageAndStatus(FORBIDDEN, 'phone.startBridgeCall.forbidden')
        }
        // validate staff
        else if (!p1.owner.all.contains(staff)) {
            resultFactory.failWithMessageAndStatus(FORBIDDEN, 'phone.notOwner')
        }
        else if (staff.personalPhoneNumber) {
            resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                'phone.startBridgeCall.noPersonalNumber')
        }
        else {
            new ResultList(phoneService.startBridgeCall(phone, c1, staff))
        }
    }
    Result<FeaturedAnnouncement> sendAnnouncement(String message,
        DateTime expiresAt, Staff staff) {
        // validate expiration
        if (expiresAt.isBeforeNow()) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "phone.sendAnnouncement.expiresInPast")
        }
        // validate staff
        if (!this.owner.all.contains(staff)) {
            return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                "phone.notOwner")
        }
        // collect relevant classes
        List<IncomingSession> textSubs = IncomingSession.subscribedToText(this).list(),
            callSubs = IncomingSession.subscribedToCall(this).list()
        String identifier = this.name
        // send announcements
        ResultMap<String,RecordItemReceipt> textRes = phoneService.startTextAnnouncement(phone,
            message, identifier, textSubs, staff).logFail("Phone.sendAnnouncement: text")
        ResultMap<String,RecordItemReceipt> callRes = phoneService.startCallAnnouncement(phone,
            message, identifier, callSubs, staff).logFail("Phone.sendAnnouncement: call")
        // build announcements class
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:this,
            expiresAt:expiresAt, message:message)
        List<IncomingSession> successTexts = textSubs.findAll {
            textRes.isSuccess(it.numberAsString)
        }, successCalls = callSubs.findAll {
            callRes.isSuccess(it.numberAsString)
        }
        announce.addToReceipts(RecordItemType.TEXT, successTexts)
            .logFail("Phone.sendAnnouncement: add text announce receipts")
        announce.addToReceipts(RecordItemType.CALL, successCalls)
            .logFail("Phone.sendAnnouncement: add call announce receipts")
        if (announce.numReceipts > 0 && (textSubs || callSubs)) {
            if (announce.save()) {
                resultFactory.success(announce)
            }
            else { resultFactory.failWithValidationErrors(announce.errors) }
        }
        // return error if all subscribers failed to receive announcement
        else {
            resultFactory.failWithResultsAndStatus(INTERNAL_SERVER_ERROR,
               textRes.results + callRes.results)
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
        else if (this.announcements) {
            switch (text.contents) {
                case Constants.TEXT_SEE_ANNOUNCEMENTS:
                    Collection<FeaturedAnnouncement> announces = this.announcements
                    announces.each { FeaturedAnnouncement announce ->
                        announce.addToReceipts(RecordItemType.TEXT, session)
                            .logFail("Phone.receiveText: add announce receipt")
                    }
                    twimlBuilder.buildXmlFor(TextResponse.ANNOUNCEMENTS,
                        [announcements:announces])
                    break
                case Constants.TEXT_SUBSCRIBE:
                    session.isSubscribedToText = true
                    twimlBuilder.buildXmlFor(TextResponse.SUBSCRIBED)
                    break
                case Constants.TEXT_UNSUBSCRIBE:
                    session.isSubscribedToText = false
                    twimlBuilder.buildXmlFor(TextResponse.UNSUBSCRIBED)
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
        else if (this.owner.any { it.personalPhoneAsString == session.numberAsString }) {
            Staff staff = this.owner.find { it.personalPhoneAsString == session.numberAsString }
            phoneService.handleSelfCall(apiId, digits, staff)
        }
        else if (this.announcements) {
            phoneService.handleAnnouncementCall(this, apiId, digits, session)
        }
        else { phoneService.relayCall(this, apiId, session) }
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
                })
        }
        else {
            twimlBuilder.buildXmlFor(CallResponse.VOICEMAIL, [name:this.name])
        }
    }
    // staff must pick up and press any number to start the bridge
    Result<Closure> confirmBridgeCall(Contact c1) {
        twimlBuilder.buildXmlFor(CallResponse.CONFIRM_BRIDGE, [contact:c1,
            linkParams:[contactId:c1?.contactId, handle:CallResponse.FINISH_BRIDGE]])
    }
    Result<Closure> finishBridgeCall(Contact c1) {
        twimlBuilder.buildXmlFor(CallResponse.FINISH_BRIDGE, [contact:c1])
    }
    Result<Closure> completeCallAnnouncement(String digits, String message,
        String identifier, IncomingSession session) {
        if (digits == Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE) {
            session.isSubscribedToCall = false
            twimlBuilder.buildXmlFor(CallResponse.UNSUBSCRIBED)
        }
        else {
            twimlBuilder.buildXmlFor(CallResponse.ANNOUNCEMENT_AND_DIGITS,
                [message:message, identifier:identifier])
        }
    }
}
