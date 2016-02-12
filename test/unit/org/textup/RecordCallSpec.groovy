package org.textup

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.codehaus.groovy.grails.commons.GrailsApplication
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
            instance.grailsApplication = [getFlatConfig:{
                ["textup.voicemailBucketName":"bucket"]
            }] as GrailsApplication
            instance.s3Service = [generatePresignedUrl: { GeneratePresignedUrlRequest req ->
                new URL("${urlRoot}${req.key}")
            }] as AmazonS3Client
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
