package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.type.AuthorType
import org.textup.type.ReceiptStatus
import org.textup.type.RecordItemType
import org.textup.util.CustomSpec
import org.textup.validator.Author
import org.textup.validator.TempRecordReceipt

class RecordItemReceiptJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling receipt"() {
        given: "receipt"
        TempRecordReceipt r1 = new TempRecordReceipt(status:ReceiptStatus.BUSY,
            apiId:"apiId", contactNumberAsString:"1112223333")
        assert r1.validate()
        rText1.addReceipt(r1)
        rText1.save(flush:true, failOnError:true)
        RecordItemReceipt rpt1 = rText1.receipts[0]

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(rpt1 as JSON) as Map
    	}

    	then:
        json.id == rpt1.id
        json.status ==  rpt1.status.toString()
        json.contactNumber == rpt1.contactNumber.e164PhoneNumber
    }
}
