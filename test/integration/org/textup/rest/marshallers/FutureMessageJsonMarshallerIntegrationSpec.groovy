package org.textup.rest.marshallers

import grails.converters.JSON
import org.joda.time.DateTime
import org.textup.*
import org.textup.types.FutureMessageType
import org.textup.util.CustomSpec

class FutureMessageJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    protected boolean validate(Map json, FutureMessage fMsg) {
        assert json.id == fMsg.id
        assert json.whenCreated == fMsg.whenCreated.toString()
        assert json.startDate == fMsg.startDate.toString()
        assert json.notifySelf == fMsg.notifySelf
        assert json.type == fMsg.type.toString()
        assert json.message == fMsg.message
        assert json.isDone == fMsg.isReallyDone
        // always will be null because we aren't actually scheduling
        assert json.timesTriggered == null
        assert json.nextFireDate == null
        true
    }

    void "test marshalling future message"() {
        given: "a nonrepeating future message"
        FutureMessage fMsg = new FutureMessage(type:FutureMessageType.CALL,
            message:"hi", record:c1.record)
        fMsg.save(flush:true, failOnError:true)

        when: "we marshal this message"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(fMsg as JSON) as Map
        }

        then:
        validate(json, fMsg)
        json.repeatIntervalInDays == null
        json.endDate == null
        json.repeatCount == null
        json.timesTriggered == null
        json.nextFireDate == null

        when: "we make this message repeating via repeatCount then marshal"
        fMsg.repeatCount = 10
        fMsg.save(flush:true, failOnError:true)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(fMsg as JSON) as Map
        }

        then:
        validate(json, fMsg)
        json.repeatIntervalInDays == fMsg.repeatIntervalInDays
        json.endDate == null
        json.repeatCount == fMsg.repeatCount

        when: "we make this message repeating via endDate then marshal"
        fMsg.repeatCount = null
        fMsg.endDate = DateTime.now().plusDays(1)
        fMsg.save(flush:true, failOnError:true)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(fMsg as JSON) as Map
        }

        then:
        validate(json, fMsg)
        json.repeatIntervalInDays == fMsg.repeatIntervalInDays
        json.endDate == fMsg.endDate.toString()
        json.repeatCount == null
    }
}
