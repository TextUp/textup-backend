package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.constraint.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode(excludes = "owner")
@GrailsTypeChecked
class Phone implements ReadOnlyPhone, WithMedia, WithId, CanSave<Phone> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    CustomAccountDetails customAccount
    DateTime whenCreated = JodaUtils.utcNow()
    PhoneOwnership owner

    String apiId
    String numberAsString

    boolean useVoicemailRecordingIfPresent = false
    MediaInfo media // public media assets related to phone, for example, recorded voicemail greeting
    String awayMessage = Constants.DEFAULT_AWAY_MESSAGE
    VoiceLanguage language = VoiceLanguage.ENGLISH
    VoiceType voice = VoiceType.MALE

    static transients = ["number"]
    static hasMany = [numberHistoryEntries: PhoneNumberHistory]

    static mapping = {
        cache usage: "read-write", include: "non-lazy"
        customAccount fetch: "join"
        media fetch: "join", cascade: "save-update"
        // [NOTE] one-to-many relationships should not have `fetch: "join"` because of GORM using
        // a left outer join to fetch the data runs into issues when a max is provided
        // see: https://stackoverflow.com/a/25426734
        numberHistoryEntries cascade: "save-update"
        owner fetch: "join", cascade: "all-delete-orphan"
        whenCreated type: PersistentDateTime
    }
    static constraints = {
        apiId blank: true, nullable: true, unique: true
        voice blank: false, nullable: false
        numberAsString blank: true, nullable: true, phoneNumber: PhoneNumberConstraint.PARAM_ALLOW_BLANK,
            validator: { String num, Phone obj ->
                //phone number must be unique for phones
                if (num && Utils.<Boolean>doWithoutFlush {
                        Phones.buildActiveForNumber(PhoneNumber.create(num))
                            .build(CriteriaUtils.forNotIdIfPresent(obj.id))
                            .count() > 0
                    }) {
                    return ["phone.numberAsString.duplicate"]
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
        PhoneOwnership.tryCreate(p1, ownerId, type)
            .then { PhoneOwnership own1 ->
                p1.owner = own1
                DomainUtils.trySave(p1, ResultStatus.CREATED)
            }
    }

    // Methods
    // -------

    boolean isActive() { getNumber().validate() }

    Collection<PhoneNumber> buildNumbersForMonth(Integer month, Integer year) {
        if (numberHistoryEntries && month && year) {
            Collection<PhoneNumber> pNums = numberHistoryEntries
                .findAll { PhoneNumberHistory nh1 -> nh1.includes(month, year) }
                *.numberIfPresent
            CollectionUtils.ensureNoNull(pNums.unique())
        }
        else { [] }
    }

    Result<String> tryActivate(BasePhoneNumber bNum, String newApiId) {
        apiId = newApiId
        number = bNum
        PhoneUtils.tryAddChangeToHistory(this, bNum)
            .then { IOCUtils.resultFactory.success(getPersistentValue("apiId") as String) }
    }

    Result<String> tryDeactivate() {
        apiId = null
        numberAsString = null
        PhoneUtils.tryAddChangeToHistory(this, null)
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

    @Override
    PhoneNumber getNumber() { PhoneNumber.create(numberAsString) }

    String getCustomAccountId() { customAccount?.accountId }

    @Override
    ReadOnlyMediaInfo getReadOnlyMedia() { media }

    URL getVoicemailGreetingUrl() {
        media?.getMostRecentByType(MediaType.AUDIO_TYPES)?.sendVersion?.link
    }
}
