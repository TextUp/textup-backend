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
        cache usage: "read-write", include: "non-lazy"
        whenCreated type: PersistentDateTime
        owner fetch: "join", cascade: "all-delete-orphan"
        customAccount fetch: "join"
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
        owner cascadeValidation: true
        media nullable: true, cascadeValidation: true
        customAccount nullable: true
    }

    // Methods
    // -------

    Phone deactivate() {
        this.numberAsString = null
        this.apiId = null
        this
    }

    String buildAwayMessage() {
        awayMessage + " " + owner?.buildOrganization()?.awayMessageSuffix
    }

    // Properties
    // ----------

    String getName() { owner.buildName() }

    boolean getIsActive() { getNumber().validate() }

    void setNumber(BasePhoneNumber pNum) { numberAsString = pNum?.number }

    PhoneNumber getNumber() { PhoneNumber.create(numberAsString) }

    String getCustomAccountId() { customAccount?.accountId }

    @Override
    ReadOnlyMediaInfo getReadOnlyMedia() { media }

    URL getVoicemailGreetingUrl() {
        media?.getMostRecentByType(MediaType.AUDIO_TYPES)?.sendVersion?.link
    }
}
