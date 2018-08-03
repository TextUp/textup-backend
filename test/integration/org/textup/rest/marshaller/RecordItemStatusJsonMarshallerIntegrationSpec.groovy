package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.type.*
import org.textup.util.TestHelpers
import spock.lang.*

class RecordItemStatusJsonMarshallerIntegrationSpec extends Specification {

    def grailsApplication

    void "test marshalling receipt"() {
        given:
        RecordItemStatus rStat1 = new RecordItemStatus()

        when: "empty"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(rStat1 as JSON)
        }

        then:
        json.success instanceof Collection
        json.success.isEmpty() == true
        json.pending instanceof Collection
        json.pending.isEmpty() == true
        json.busy instanceof Collection
        json.busy.isEmpty() == true
        json.failed instanceof Collection
        json.failed.isEmpty() == true

        when: "populated with receipts of varying statuses"
        int numFailed = 2
        int numSuccess = 3
        numFailed.times { rStat1.add(TestHelpers.buildReceipt(ReceiptStatus.FAILED)) }
        numSuccess.times { rStat1.add(TestHelpers.buildReceipt(ReceiptStatus.SUCCESS)) }
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(rStat1 as JSON)
        }

        then:
        json.success instanceof Collection
        json.success.size() == numSuccess
        json.success.every { it instanceof String }
        json.pending instanceof Collection
        json.pending.isEmpty() == true
        json.busy instanceof Collection
        json.busy.isEmpty() == true
        json.failed instanceof Collection
        json.failed.size() == numFailed
        json.failed.every { it instanceof String }
    }
}
