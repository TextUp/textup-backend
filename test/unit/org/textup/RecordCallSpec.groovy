package org.textup

import com.amazonaws.HttpMethod
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.types.ReceiptStatus
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt])
@TestMixin(HibernateTestMixin)
class RecordCallSpec extends Specification {

    String urlRoot = "http://www.example.com/?key="

    def setup() {
        RecordCall.metaClass.constructor = { Map m ->
            RecordCall instance = new RecordCall()
            instance.properties = m
            instance.storageService = [generateAuthLink:{ String k, HttpMethod v ->
                new Result(success:true, payload:new URL("${urlRoot}${k}"))
            }] as StorageService
            instance
        }
    }

    void "test getting voicemail url"() {
        when: "we don't have a voicemail"
        Record rec = new Record()
        assert rec.save(flush:true, failOnError:true)
        RecordCall call = new RecordCall(record:rec)

        then: "empty string"
        call.validate() == true
        call.voicemailUrl == ""

        when: "we do have voicemail AND no receipts"
        call.hasVoicemail = true
        call.voicemailInSeconds = 88
        assert call.save(flush:true, failOnError:true)

        then: "empty string"
        call.voicemailUrl == ""

        when: "we add a SUCCESS receipts"
        String apiId = "testing123"
        call.addToReceipts(apiId:apiId, receivedByAsString:"1112223333",
            status:ReceiptStatus.SUCCESS)
        assert call.save(flush:true, failOnError:true)

        then: "can get url"
        call.voicemailUrl == "${urlRoot}${apiId}"
    }
}
