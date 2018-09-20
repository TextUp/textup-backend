package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.util.*
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
        String uid = TestHelpers.randString()
        url = service.getVoicemailUrl(uid)

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
        ResultGroup<RecordCall> resGroup = service.storeVoicemail("nonexistent", 12)

        then: "empty result list"
        resGroup.isEmpty == true
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "apiId corresponds to NOT RecordCall"
        String apiId = TestHelpers.randString()
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
        apiId = TestHelpers.randString()
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
        resGroup.successes.size() == 2
        resGroup.payload.size() == 2
        resGroup.payload.every { it instanceof RecordCall }
        resGroup.payload.every { it.voicemailKey == apiId }
        resGroup.payload.every { it.hasVoicemail }
        resGroup.payload.every { it.voicemailInSeconds == dur }
        resGroup.payload.every { it.record.lastRecordActivity.isAfter(recAct) }
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline
    }
}
