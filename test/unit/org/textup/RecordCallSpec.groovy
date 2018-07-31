package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.type.ReceiptStatus
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt, Organization, Location,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordCallSpec extends Specification {

    String urlRoot = "http://www.example.com/?key="

    def setup() {
        RecordCall.metaClass.constructor = { Map m ->
            RecordCall instance = new RecordCall()
            instance.properties = m
            instance.voicemailService = [
                getVoicemailUrl:{ RecordItemReceipt k ->
                    (k ? "${urlRoot}${k.apiId}" : "") as String
                }
            ] as VoicemailService
            instance
        }
    }

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
        when: "we don't have a voicemail"
        Record rec = new Record()
        assert rec.save(flush:true, failOnError:true)
        RecordCall call = new RecordCall(record:rec)

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
        call.hasAwayMessage = true
        call.voicemailInSeconds = 88
        assert call.save(flush:true, failOnError:true)

        then: "empty string"
        call.hasVoicemail == true
        call.voicemailUrl == ""

        when: "we add a SUCCESS receipts"
        String apiId = "testing123"
        call.addToReceipts(apiId:apiId, contactNumberAsString:"1112223333",
            status:ReceiptStatus.SUCCESS)
        assert call.save(flush:true, failOnError:true)

        then: "can get url -- see mock"
        call.hasVoicemail == true
        call.voicemailUrl == "${urlRoot}${apiId}"
    }
}
