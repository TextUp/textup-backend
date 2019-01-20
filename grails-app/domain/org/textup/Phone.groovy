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
class Phone implements WithMedia, WithId, Saveable<Phone> {

    boolean useVoicemailRecordingIfPresent = false
    CustomAccountDetails customAccount
    DateTime whenCreated = DateTimeUtils.now()
    MediaInfo media // public media assets related to phone, for example, recorded voicemail greeting
    PhoneOwnership owner
    String apiId
    String awayMessage = Constants.DEFAULT_AWAY_MESSAGE
    String numberAsString
    VoiceLanguage language = VoiceLanguage.ENGLISH
    VoiceType voice = VoiceType.MALE

    static transients = ["number"]
    static mapping = {
        cache usage: "read-write", include: "non-lazy"
        whenCreated type: PersistentDateTime
        owner fetch: "join", cascade: "all-delete-orphan"
        customAccount fetch: "join"
        media fetch: "join", cascade: "save-update"
    }
    static constraints = {
        apiId blank: true, nullable: true, unique: true
        voice blank: false, nullable: false
        numberAsString blank: true, nullable: true, validator: { String num, Phone obj ->
            if (num) {
                if (!ValidationUtils.isValidPhoneNumber(num)) {
                    return ["format"]
                }
                //phone number must be unique for phones
                if (Utils.<Boolean>doWithoutFlush {
                        Phones.buildForNumber(PhoneNumber.create(num))
                            .build(CriteriaUtils.forNotId(obj.id))
                            .count() > 0
                    }) {
                    return ["duplicate"]
                }
            }
        }
        awayMessage blank: false, size: 1..(ValidationUtils.TEXT_BODY_LENGTH * 2)
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

    Phone deactivate() {  // TODO add history
        numberAsString = null
        apiId = null
        this
    }

    String buildAwayMessage() {
        awayMessage + " " + owner?.buildOrganization()?.awayMessageSuffix
    }

    // Properties
    // ----------

    boolean getIsActive() { getNumber().validate() } // TODO add history

    void setNumber(BasePhoneNumber pNum) { numberAsString = pNum?.number }

    PhoneNumber getNumber() { PhoneNumber.create(numberAsString) }

    String getCustomAccountId() { customAccount?.accountId }

    @Override
    ReadOnlyMediaInfo getReadOnlyMedia() { media }

    URL getVoicemailGreetingUrl() {
        media?.getMostRecentByType(MediaType.AUDIO_TYPES)?.sendVersion?.link
    }
}
