package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.joda.time.DateTime
import org.textup.util.CustomSpec
import spock.lang.Specification

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
@TestFor(VoicemailService)
class VoicemailServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    void "test getting voicemail url"() {
        given:
        service.storageService = [
            generateAuthLink: { String apiId ->
                new Result(payload: new URL("http://www.example.com?q=${apiId}"))
            }
        ] as StorageService

        when: "null receipt"
        String url = service.getVoicemailUrl(null)

        then:
        url == ""

        when: "present receipt"
        String uid = UUID.randomUUID().toString()
        url = service.getVoicemailUrl([apiId: uid] as RecordItemReceipt)

        then:
        url instanceof String
        url.contains(uid)
    }

    void "test storing voicemail"() {
        given: "session with no corresponding contact"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        service.socketService = [
            sendItems:{ List<RecordItem> items -> new ResultGroup()}
        ] as SocketService

        when: "none voicemails found for apiId"
        ResultGroup<RecordItemReceipt> resGroup = service.storeVoicemail("nonexistent", 12)

        then: "empty result list"
        resGroup.isEmpty == true
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "apiId corresponds to NOT RecordCall"
        String apiId = "thisoneisunique!!!"
        rText1.addToReceipts(apiId:apiId, contactNumberAsString:"1234449309")
        rText1.save(flush:true, failOnError:true)
        iBaseline = RecordCall.count()
        rBaseline = RecordItemReceipt.count()
        resGroup = service.storeVoicemail(apiId, 12)

        then: "empty result list"
        resGroup.isEmpty == true
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "apiId corresponds to multiple RecordCalls"
        apiId = "thisisthe?!best!!!"
        RecordCall rCall1 = c1.record.storeOutgoingCall().payload,
            rCall2 = c1.record.storeOutgoingCall().payload
        [rCall1, rCall2].each {
            it.addToReceipts(apiId:apiId, contactNumberAsString:"1234449309")
            it.save(flush:true, failOnError:true)
        }
        int dur = 12
        DateTime recAct = rCall1.record.lastRecordActivity
        iBaseline = RecordCall.count()
        rBaseline = RecordItemReceipt.count()
        resGroup = service.storeVoicemail(apiId, dur)

        then:
        resGroup.anySuccesses == true
        resGroup.payload.size() == 2
        resGroup.successes.size() == 2
        resGroup.payload[0].item.hasVoicemail == true
        resGroup.payload[0].item.voicemailInSeconds == dur
        resGroup.payload[0].item.record.lastRecordActivity.isAfter(recAct)
        resGroup.payload[1].item.hasVoicemail == true
        resGroup.payload[1].item.voicemailInSeconds == dur
        resGroup.payload[1].item.record.lastRecordActivity.isAfter(recAct)
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline
    }
}
