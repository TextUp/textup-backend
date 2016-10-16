package org.textup.rest.marshallers

import grails.converters.JSON
import org.textup.*
import org.textup.types.AuthorType
import org.textup.types.ReceiptStatus
import org.textup.types.RecordItemType
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
            apiId:"apiId", receivedByAsString:"1112223333")
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
        json.receivedBy == rpt1.receivedBy.e164PhoneNumber
    }
}
