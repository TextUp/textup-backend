package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.type.ReceiptStatus
import org.textup.util.*
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt, Organization, Location,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordCallSpec extends Specification {

    void "test constraints"() {
        given:
        Record rec1 = new Record()
        rec1.save(flush:true, failOnError:true)

        when: "empty"
        RecordCall rCall1 = new RecordCall()

        then: "invalid"
        rCall1.validate() == false
        rCall1.errors.errorCount == 1

        when: "with record"
        rCall1.record = rec1

        then: "valid"
        rCall1.validate() == true
    }

    void "test getting voicemail url"() {
        given:
        VoicemailService mockVoicemailService = Mock(VoicemailService)
        mockVoicemailService.getVoicemailUrl(*_) >> { String key -> key }

        when: "we don't have a voicemail"
        Record rec = new Record()
        assert rec.save(flush:true, failOnError:true)
        RecordCall call = new RecordCall(record:rec)
        call.voicemailService = mockVoicemailService

        then: "empty string"
        call.validate() == true
        call.hasVoicemail == false
        call.voicemailUrl == ""

        when: "we have away message, but voicemail duration is 0"
        call.hasAwayMessage = true
        call.voicemailInSeconds = 0

        then: "effectively no voicemail, such as when user hangs up before recording a voicemail"
        call.validate() == true
        call.hasVoicemail == false
        call.voicemailUrl == ""

        when: "we do have voicemail AND no receipts"
        String apiId = "testing123"
        call.hasAwayMessage = true
        call.voicemailInSeconds = 88
        call.voicemailKey = apiId
        assert call.save(flush:true, failOnError:true)

        then: "can get url -- see mock"
        call.hasVoicemail == true
        call.voicemailUrl == apiId
    }

    void "test getting duration in seconds and excluding receipt with longest duration"() {
        given: "a valid record call"
        Record rec = new Record()
        assert rec.save(flush:true, failOnError:true)
        RecordCall call = new RecordCall(record:rec)

        when: "no receipts"
        assert call.receipts == null

        then:
        call.durationInSeconds == 0
        call.showOnlyContactReceipts() == []

        when: "one receipt without numBillable"
        RecordItemReceipt rpt1 = TestHelpers.buildReceipt()
        rpt1.numBillable = null
        call.addToReceipts(rpt1)

        then: "show only contact receipts excludes null"
        call.durationInSeconds == 0
        call.showOnlyContactReceipts() == []

        when: "multiple receipts with varying numBillable"
        RecordItemReceipt rpt2 = TestHelpers.buildReceipt()
        rpt2.numBillable = 12
        RecordItemReceipt rpt3 = TestHelpers.buildReceipt()
        rpt3.numBillable = 88
        [rpt2, rpt3].each(call.&addToReceipts)

        then:
        call.durationInSeconds == 88
        call.showOnlyContactReceipts().size() == 1
        call.showOnlyContactReceipts().every { it in [rpt2] }
    }

    void "test grouping receipts by status for incoming versus outgoing"() {
        given: "a valid record call with multiple receipts with varying numBillable"
        Record rec = new Record()
        assert rec.save(flush:true, failOnError:true)
        RecordCall call = new RecordCall(record:rec)
        RecordItemReceipt rpt1 = TestHelpers.buildReceipt(ReceiptStatus.PENDING),
            rpt2 = TestHelpers.buildReceipt(ReceiptStatus.PENDING),
            rpt3 = TestHelpers.buildReceipt(ReceiptStatus.PENDING)
        rpt1.numBillable = null
        rpt2.numBillable = 12
        rpt3.numBillable = 88
        [rpt1, rpt2, rpt3].each(call.&addToReceipts)

        when: "call is incoming"
        call.outgoing = false

        then: "show all receipts"
        call.groupReceiptsByStatus().pending.size() == 3

        when: "call is outgoing"
        call.outgoing = true

        then: "exclude receipt with the longest or null durations"
        call.groupReceiptsByStatus().pending.size() == 1 // exclude rp1 and rpt3
    }
}
