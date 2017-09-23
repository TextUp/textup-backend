package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.type.ReceiptStatus
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt, Organization, Location])
@TestMixin(HibernateTestMixin)
class RecordCallSpec extends Specification {

    String urlRoot = "http://www.example.com/?key="

    def setup() {
        RecordCall.metaClass.constructor = { Map m ->
            RecordCall instance = new RecordCall()
            instance.properties = m
            instance.storageService = [generateAuthLink:{ String k ->
                new Result(status:ResultStatus.OK, payload:new URL("${urlRoot}${k}"))
            }] as StorageService
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

        when: "with too-long call contents"
        rCall1.callContents = '''
            Far far away, behind the word mountains, far from the countries Vokalia and
            Consonantia, there live the blind texts. Separated they live in Bookmarksgrove
            right at the coast of the Semantics, a large language ocean. A small river
            named Duden flows by their place and supplies it with the necessary regelialia.
            It is a paradisemati
        '''

        then:
        rCall1.validate() == false
        rCall1.errors.errorCount == 1

        when: "with appropriate length call contents"
        rCall1.callContents = "I am an appropriate length"

        then:
        rCall1.validate() == true
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
