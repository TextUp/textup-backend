package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.test.*
import org.textup.util.*
import org.textup.validator.*

class RecordItemRequestJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
        setupIntegrationData()
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    void "test marshalling"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        c1.phone = p1
        c1.record.storeOutgoingCall()

        Record.withSession { it.flush() }

        iReq.groupByEntity = false

        when: "without pagination options"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(iReq as JSON)
        }

        then:
        json.totalNumItems > 0
        json.totalNumItems == iReq.countRecordItems()
        json.maxAllowedNumItems == Constants.MAX_PAGINATION_MAX
        json.sections.size() == 1
        json.sections[0].recordItems.size() == iReq.recordItems.size()

        when: "with pagination options"
        Utils.trySetOnRequest(Constants.REQUEST_PAGINATION_OPTIONS, [offset: 1000])

        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(iReq as JSON)
        }

        then:
        json.totalNumItems > 0
        json.totalNumItems == iReq.countRecordItems()
        json.maxAllowedNumItems == Constants.MAX_PAGINATION_MAX
        json.sections.size() == 1
        json.sections[0].recordItems.size() == 0
    }
}
