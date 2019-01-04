package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode(excludes = "owner")
class Phone implements WithMedia, WithId {

    AvailableTextAction availableTextAction = AvailableTextAction.NOTIFY_TEXT_IMMEDIATELY // TODO
    boolean sendPreviewLinkWithNotification = true // TODO
    boolean useVoicemailRecordingIfPresent = false
    CustomAccountDetails customAccount
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    MediaInfo media // public media assets related to phone, for example, recorded voicemail greeting
    PhoneOwnership owner
    String apiId
    String awayMessage = Constants.DEFAULT_AWAY_MESSAGE
    String numberAsString
    VoiceLanguage language = VoiceLanguage.ENGLISH
    VoiceType voice = VoiceType.MALE

    static transients = ["number"]
    static mapping = {
        whenCreated type: PersistentDateTime
        owner fetch: "join", cascade: "all-delete-orphan"
        media lazy: false, cascade: "save-update"
    }
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
        awayMessage blank:false, size:1..(Constants.TEXT_LENGTH * 2)
        media nullable: true, cascadeValidation: true
        customAccount nullable: true
    }

    // Static methods
    // --------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static HashSet<Phone> getPhonesForRecords(Collection<Record> recs) {
        HashSet<Phone> allPhones = new HashSet<>()
        if (recs) {
            List<Phone> phones = Phones.createCriteria().listDistinct {
                or {
                    "in"("id", Phone.forPhoneIdsFromRecords(Contact, recs))
                    "in"("id", Phone.forPhoneIdsFromRecords(ContactTag, recs))
                    "in"("id", Phone.forPhoneIdsFromSharing(recs))
                }
            } as List<Phone>
            allPhones.addAll(phones)
        }
        allPhones
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static DetachedCriteria<Long> forPhoneIdsFromRecords(
        Class<? extends WithPhoneAndRecord> clazz, Collection<Record> records) {

        return new DetachedCriteria(clazz).build {
            projections { property("phone.id") }
            CriteriaUtils.inList(delegate, "record", records)
            eq("isDeleted", false)
        }
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static DetachedCriteria<Long> forPhoneIdsFromSharing(Collection<Record> records) {
        return new DetachedCriteria(SharedContact).build {
            projections { property("sharedWith.id") }
            contact {
                CriteriaUtils.inList(delegate, "record", records)
                eq("isDeleted", false)
            }
            or {
                isNull("dateExpired") // still active if null
                gt("dateExpired", DateTime.now())
            }
        }
    }

    // TODO
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Map<Phone, List<RecordItem>>> findEveryByItems(Collection<RecordItem> rItems) {

        PhoneRecord.createCriteria().list {
            CriteriaUtils.inList(delegate, "record". rItems*.record)
        }

        // TODO
        // // Contact, ContactTag and SharedContact are all bridge classes
        // Collection<Record> recs = rItems*.record
        // List<Phone> phones = Phones.createCriteria().listDistinct {
        //     projections {
        //         property("id")

        //     }
        //     or {
        //         "in"("id", Phone.forPhoneIdsFromRecords(Contact, recs))
        //         "in"("id", Phone.forPhoneIdsFromRecords(ContactTag, recs))
        //         "in"("id", Phone.forPhoneIdsFromSharing(recs))
        //     }
        // } as List<Phone>
    }

    // TODO no typing?
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<Phone> findByOwner(Long ownerId, PhoneOwnershipType type) {
        List<Phone> phones = Phone.createCriteria().list(max: 1) {
            owner {
                eq("ownerId", ownerId)
                eq("type", type)
            }
        } as List<Phone>
        if (phones) {
            IOCUtils.resultFactory.success(phones[0])
        }
        else {
            // TODO add message
            IOCUtils.resultFactory.failWithCodeAndStatus("phone.notFound", ResultStatus.NOT_FOUND)
        }
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

    // Sharing
    // -------

    boolean canShare(Phone sWith) {
        if (!sWith) { return false }
        Collection<Team> myTeams = Team.listForStaffs(owner.buildAllStaff()),
            sharedWithTeams = Team.listForStaffs(sWith.owner.buildAllStaff())
        HashSet<Team> allowedTeams = new HashSet<>(myTeams)
        sharedWithTeams.any { it in allowedTeams }
    }
    Result<SharedContact> share(Contact c1, Phone sWith, SharePermission perm) {
        if (c1?.phone?.id != id) {
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
        if (contact?.phone?.id != id) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("phone.contactNotMine",
                ResultStatus.BAD_REQUEST, [contact?.getNameOrNumber()])
        }
        List<SharedContact> shareds = SharedContact.listForContact(contact)
        shareds?.each { SharedContact sc -> sc.stopSharing() }
        IOCUtils.resultFactory.success()
    }

    // Property Access
    // ---------------

    String buildAwayMessage() {
        awayMessage + " " + owner?.buildOrganization()?.awayMessageSuffix
    }

    String getName() {
        owner.buildName()
    }
    boolean getIsActive() {
        getNumber().validate()
    }
    void setNumber(BasePhoneNumber pNum) {
        numberAsString = pNum?.number
    }
    PhoneNumber getNumber() {
        PhoneNumber.create(numberAsString)
    }
    String getCustomAccountId() {
        customAccount?.accountId
    }

    int countTags() {
        ContactTag.countByPhoneAndIsDeleted(this, false)
    }
    List<ContactTag> getTags(Map params=[:]) {
        ContactTag.findAllByPhoneAndIsDeleted(this, false, params + [sort: "name", order: "desc"])
    }

    // TODO remove
    // //Optional specify 'status' corresponding to valid contact statuses
    // int countContacts(Map params=[:]) {
    //     if (params == null) {
    //         return 0
    //     }
    //     Contact.countForPhoneAndStatuses(this,
    //         TypeConversionUtils.toEnumList(ContactStatus, params.statuses, ContactStatus.ACTIVE_STATUSES))
    // }
    // //Optional specify 'status' corresponding to valid contact statuses
    // List<Contactable> getContacts(Map params=[:]) {
    //     if (params == null) {
    //         return []
    //     }
    //     Collection<ContactStatus> statusEnums =
    //         TypeConversionUtils.toEnumList(ContactStatus, params.statuses, ContactStatus.ACTIVE_STATUSES)
    //     //get contacts, both mine and those shared with me
    //     List<Contact> contactList = Contact.listForPhoneAndStatuses(this, statusEnums, params)
    //     replaceContactsWithShared(contactList)
    // }
    // int countContacts(String query) {
    //     if (query == null) {
    //         return 0
    //     }
    //     Contact.countForPhoneAndSearch(this, StringUtils.toQuery(query))
    // }
    // List<Contactable> getContacts(String query, Map params=[:]) {
    //     if (query == null) {
    //         return []
    //     }
    //     //get contacts, both mine and those shared with me
    //     replaceContactsWithShared(Contact.listForPhoneAndSearch(this, StringUtils.toQuery(query), params))
    // }
    // // TODO remove
    // protected List<Contactable> replaceContactsWithShared(List<Contact> contacts) {
    // }

    // // Remove
    // int countSharedWithMe() {
    //     SharedContact.countSharedWithMe(this)
    // }
    // List<SharedContact> getSharedWithMe(Map params=[:]) {
    //     SharedContact.listSharedWithMe(this, params)
    // }
    // int countSharedByMe() {
    //     SharedContact.countSharedByMe(this)
    // }
    // List<Contact> getSharedByMe(Map params=[:]) {
    //     SharedContact.listSharedByMe(this, params)
    // }

    // // TODO remove
    // int countAnnouncements() {
    //     FeaturedAnnouncement.countForPhone(this)
    // }
    // List<FeaturedAnnouncement> getAnnouncements(Map params=[:]) {
    //     FeaturedAnnouncement.listForPhone(this, params)
    // }

    // TODO remove
    // int countSessions() {
    //     IncomingSession.countByPhone(this)
    // }
    // List<IncomingSession> getSessions(Map params=[:]) {
    //     IncomingSession.findAllByPhone(this, params)
    // }
    // int countCallSubscribedSessions() {
    //     IncomingSession.countByPhoneAndIsSubscribedToCall(this, true)
    // }
    // List<IncomingSession> getCallSubscribedSessions(Map params=[:]) {
    //     IncomingSession.findAllByPhoneAndIsSubscribedToCall(this, true, params)
    // }
    // int countTextSubscribedSessions() {
    //     IncomingSession.countByPhoneAndIsSubscribedToText(this, true)
    // }
    // List<IncomingSession> getTextSubscribedSessions(Map params=[:]) {
    //     IncomingSession.findAllByPhoneAndIsSubscribedToText(this, true, params)
    // }

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
