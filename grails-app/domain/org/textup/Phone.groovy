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
class Phone implements ReadOnlyPhone, WithMedia, WithId, Saveable<Phone> {

    CustomAccountDetails customAccount
    DateTime whenCreated = DateTimeUtils.now()
    PhoneOwnership owner

    String apiId
    String numberAsString

    boolean useVoicemailRecordingIfPresent = false
    MediaInfo media // public media assets related to phone, for example, recorded voicemail greeting
    String awayMessage = Constants.DEFAULT_AWAY_MESSAGE
    VoiceLanguage language = VoiceLanguage.ENGLISH
    VoiceType voice = VoiceType.MALE

    static hasMany = [numberHistoryEntries: PhoneNumberHistory]
    static mapping = {
        cache usage: "read-write", include: "non-lazy"
        customAccount fetch: "join"
        media fetch: "join", cascade: "save-update"
        numberHistoryEntries fetch: "join", cascade: "save-update"
        owner fetch: "join", cascade: "all-delete-orphan"
        whenCreated type: PersistentDateTime
    }
    static constraints = {
        apiId blank: true, nullable: true, unique: true
        voice blank: false, nullable: false
        numberAsString blank: true, nullable: true, phoneNumber: true, validator: { String num, Phone obj ->
            //phone number must be unique for phones
            if (num && Utils.<Boolean>doWithoutFlush {
                    Phones.buildActiveForNumber(PhoneNumber.create(num))
                        .build(CriteriaUtils.forNotId(obj.id))
                        .count() > 0
                }) {
                return ["duplicate"]
            }
        }
        awayMessage blank: false, size: 1..(ValidationUtils.TEXT_BODY_LENGTH * 2)
        numberHistoryEntries cascadeValidation: true
        owner cascadeValidation: true
        media nullable: true, cascadeValidation: true
        customAccount nullable: true
    }

    static Result<Phone> tryCreate(Long ownerId, PhoneOwnershipType type) {
        Phone p1 = new Phone()
        p1.owner = new PhoneOwnership(ownerId: ownerId, type: type, phone: p1)
        DomainUtils.trySave(p1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    boolean isActive() { getNumber().validate() }

    Collection<PhoneNumber> buildNumbersForMonth(Integer month, Integer year) {
        if (numberHistoryEntries && month && year) {
            Collection<PhoneNumber> pNums = numberHistoryEntries
                .findAll { PhoneNumberHistory nh1 -> nh1.includes(month, year) }
                *.numberIfPresent
            CollectionUtils.ensureNoNull(pNums)
        }
        else { [] }
    }

    Result<String> tryActivate(BasePhoneNumber bNum, String newApiId) {
        apiId = newApiId
        number = bNum
        tryAddHistoryEntry(oldNumber)
            .then { IOCUtils.resultFactory.success(getPersistentValue("apiId") as String) }
    }

    Result<String> tryDeactivate() {
        apiId = null
        numberAsString = null
        tryAddHistoryEntry(oldNumber)
            .then { IOCUtils.resultFactory.success(getPersistentValue("apiId") as String) }
    }

    String buildAwayMessage() {
        awayMessage + " " + owner?.buildOrganization()?.awayMessageSuffix
    }

    @Override
    String buildName() { owner.buildName() }

    // Properties
    // ----------

    void setNumber(BasePhoneNumber bNum) { numberAsString = bNum?.number }

    PhoneNumber getNumber() { PhoneNumber.create(numberAsString) }

    String getCustomAccountId() { customAccount?.accountId }

    @Override
    ReadOnlyMediaInfo getReadOnlyMedia() { media }

    URL getVoicemailGreetingUrl() {
        media?.getMostRecentByType(MediaType.AUDIO_TYPES)?.sendVersion?.link
    }

    // Helpers
    // -------

    protected Result<Phone> tryAddHistoryEntry() {
        DateTime dt = DateTimeUtils.now()
        PhoneNumber pNum = PhoneNumber
            .tryCreate(getPersistentValue("numberAsString") as String)
            .payload
        PhoneNumberHistory.tryCreate(dt, pNum)
            .then { PhoneNumberHistory nh1 ->
                // find most recent entry before adding new one
                numberHistoryEntries?.max()?.setEndTime(dt) // phone save will cascade
                // add new entry
                addToNumberHistoryEntries(nh1)
                DomainUtils.trySave(this)
            }
    }
}
