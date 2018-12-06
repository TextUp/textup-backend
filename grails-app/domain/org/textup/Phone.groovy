package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode(excludes = "owner")
@RestApiObject(name = "Phone", description = "A TextUp phone.")
class Phone implements WithMedia, WithId {

    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)

    //unique id assigned to this phone's number
    String apiId
	String numberAsString
    PhoneOwnership owner

    @RestApiObjectField(
        description    = "public media assets related to phone, for example, recorded voicemail greeting",
        allowedType    = "MediaInfo",
        useForCreation = false)
    MediaInfo media

    @RestApiObjectField(
        description  = "Whether to use the voicemail recording for voicemails if available",
        mandatory    = false,
        allowedType  = "Boolean",
        defaultValue = "false")
    boolean useVoicemailRecordingIfPresent = false

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

    @RestApiObjectFields(params = [
        @RestApiObjectField(
            apiFieldName = "number",
            description  = "Phone number of this TextUp phone.",
            allowedType  = "String"),
         @RestApiObjectField(
            apiFieldName   = "mandatoryEmergencyMessage",
            description    = "Mandatory emergency message that will be appended to the custom away message to bring the total character length to no more than 160 characters.",
            allowedType    = "String",
            useForCreation = false),
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
            description    = "Contains availability info for a particular staff for this phone, update attributes in this object to update phone-specific availability",
            allowedType    = "StaffPolicyAvailability",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "others",
            description    = "READ ONLY, full availability information for other staff owners of this particular phone if this is a team phone.",
            allowedType    = "List<StaffPolicyAvailability>",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "requestVoicemailGreetingCall",
            description       = "Triggers a phone call to the phone number provided to update this phone's voicemail greeting. Boolean `true` also accepted and will default to the current staff member's personal phone number.",
            allowedType       = "Boolean or String",
            useForCreation    = false,
            presentInResponse = false)
    ])
    static transients = ["number"]
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
            if (Utils.<Boolean>doWithoutFlush(existsWithSameNumber)) {
                return ["duplicate"]
            }
            if (!(num.toString() ==~ /^(\d){10}$/)) {
                return ["format"]
            }
        }
        awayMessage blank:false, size:1..(Constants.TEXT_LENGTH)
        media nullable: true, cascadeValidation: true
    }
    static mapping = {
        whenCreated type:PersistentDateTime
        owner fetch:"join", cascade:"all-delete-orphan"
        media lazy: false, cascade: "save-update"
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
            this.awayMessage = StringUtils.appendGuaranteeLength(awayMsg, mandatoryMsg, maxLen).trim()
        }
    }

    // Static finders
    // --------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static HashSet<Phone> getPhonesForRecords(Collection<Record> recs) {
        if (!recs) { return new HashSet<Phone>() }
        Collection<Phone> cPhones = Contact.createCriteria().list {
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
                return IOCUtils.resultFactory.failWithValidationErrors(otherOwn.errors)
            }
        }
        // then associate this phone with new owner
        own.type = type
        own.ownerId = id
        if (own.save()) {
            IOCUtils.resultFactory.success(own)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(own.errors) }
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
            tag.language = Utils.withDefault(
                TypeConversionUtils.convertEnum(VoiceLanguage, params.language),
                this.language
            )
            if (tag.save()) {
                return IOCUtils.resultFactory.success(tag, ResultStatus.CREATED)
            }
        }
        IOCUtils.resultFactory.failWithValidationErrors(tag.errors)
    }

    // Contacts
    // --------

    Result<Contact> createContact(Map params=[:], Collection<String> numbers=[]) {
        Contact contact = new Contact([:])
        contact.phone = this
        if (params.name) contact.name = params.name
        if (params.note) contact.note = params.note
        if (params.status) contact.status = TypeConversionUtils.convertEnum(ContactStatus, params.status)
        if (params.isDeleted != null) contact.isDeleted = params.isDeleted
        // if language is provided but record is not initialized yet, need to do so in order
        // to set the language on the record object
        VoiceLanguage lang = Utils.withDefault(TypeConversionUtils.convertEnum(VoiceLanguage, params.language),
            this.language)
        if (!contact.record) {
            contact.record = new Record(language: lang)
        }
        else { contact.record.language = lang }
        // need to save once language is modified so record will be persisted at the end of transaction
        if (!contact.record.save()) {
            return IOCUtils.resultFactory.failWithValidationErrors(contact.record.errors)
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
            return IOCUtils.resultFactory.success(contact, ResultStatus.CREATED)
        }
        IOCUtils.resultFactory.failWithValidationErrors(contact.errors)
    }

    // Sharing
    // -------

    boolean canShare(Phone sWith) {
        if (!sWith) { return false }
        Collection<Team> myTeams = Team.listForStaffs(this.owner.buildAllStaff()),
            sharedWithTeams = Team.listForStaffs(sWith.owner.buildAllStaff())
        HashSet<Team> allowedTeams = new HashSet<>(myTeams)
        sharedWithTeams.any { it in allowedTeams }
    }
    Result<SharedContact> share(Contact c1, Phone sWith, SharePermission perm) {
        if (c1?.phone?.id != this.id) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("phone.contactNotMine",
                ResultStatus.BAD_REQUEST, [c1?.name])
        }
        if (!canShare(sWith)) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("phone.share.cannotShare",
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
            IOCUtils.resultFactory.success(sc)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(sc.errors) }
    }
    Result<Void> stopShare(Phone sWith) {
        List<SharedContact> shareds = SharedContact.listForSharedByAndSharedWith(this, sWith)
        shareds.each { SharedContact sc -> sc.stopSharing() }
        IOCUtils.resultFactory.success()
    }
    Result<Void> stopShare(Contact c1, Phone sWith) {
        SharedContact sc1 = SharedContact.listForContactAndSharedWith(c1, sWith, [max:1])[0]
        if (sc1) {
            sc1.stopSharing()
        }
        IOCUtils.resultFactory.success()
    }
    Result<Void> stopShare(Contact contact) {
        // Hibernate proxying magic sometimes results in either contact,phone or this phone being
        // a proxy and therefore not equal to each other when they should be. We get around this
        // problem by comparing the ids of the two objects to ascertain identity
        if (contact?.phone?.id != this.id) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("phone.contactNotMine",
                ResultStatus.BAD_REQUEST, [contact?.getNameOrNumber()])
        }
        List<SharedContact> shareds = SharedContact.listForContact(contact)
        shareds?.each { SharedContact sc -> sc.stopSharing() }
        IOCUtils.resultFactory.success()
    }

    // Property Access
    // ---------------

    String getName() {
        this.owner.buildName()
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
            TypeConversionUtils.toEnumList(ContactStatus, params.statuses, ContactStatus.ACTIVE_STATUSES))
    }
    //Optional specify 'status' corresponding to valid contact statuses
    List<Contactable> getContacts(Map params=[:]) {
        if (params == null) {
            return []
        }
        Collection<ContactStatus> statusEnums =
            TypeConversionUtils.toEnumList(ContactStatus, params.statuses, ContactStatus.ACTIVE_STATUSES)
        //get contacts, both mine and those shared with me
        List<Contact> contactList = Contact.listForPhoneAndStatuses(this, statusEnums, params)
        replaceContactsWithShared(contactList)
    }
    int countContacts(String query) {
        if (query == null) {
            return 0
        }
        Contact.countForPhoneAndSearch(this, StringUtils.toQuery(query))
    }
    List<Contactable> getContacts(String query, Map params=[:]) {
        if (query == null) {
            return []
        }
        //get contacts, both mine and those shared with me
        replaceContactsWithShared(Contact.listForPhoneAndSearch(this, StringUtils.toQuery(query), params))
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

    ReadOnlyMediaInfo getReadOnlyMedia() { media }
    URL getVoicemailGreetingUrl() {
        media?.getMostRecentByType(MediaType.AUDIO_TYPES)?.sendVersion?.link
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
            IOCUtils.resultFactory.success(own)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(own.errors) }
    }
}
