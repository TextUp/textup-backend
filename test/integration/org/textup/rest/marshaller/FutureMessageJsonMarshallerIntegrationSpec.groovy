package org.textup.rest.marshaller

import grails.converters.JSON
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.FutureMessageType
import org.textup.type.VoiceLanguage
import org.textup.util.TestHelpers
import spock.lang.*

class FutureMessageJsonMarshallerIntegrationSpec extends Specification {

    def grailsApplication
    Record rec

    def setup() {
    	rec = new Record()
        rec.save(flush: true, failOnError: true)
    }

    protected boolean validate(Map json, FutureMessage fMsg) {
        assert json.id == fMsg.id
        assert json.whenCreated == fMsg.whenCreated.toString()
        assert json.startDate == fMsg.startDate.toString()
        assert json.notifySelf == fMsg.notifySelf
        assert json.type == fMsg.type.toString()
        assert json.message == fMsg.message
        assert json.isDone == fMsg.isReallyDone
        assert json.isRepeating == fMsg.isRepeating
        assert json.language == fMsg.language.toString()
        assert json.media instanceof Map
        // always will be null because we aren't actually scheduling
        assert json.timesTriggered == null
        assert json.nextFireDate == null
        // didn't mock up contacts or tags
        assert json.contact == null
        assert json.tag == null
        true
    }

    void "test marshalling future message"() {
        given: "a nonrepeating future message"
        FutureMessage fMsg = new FutureMessage(
            type:FutureMessageType.CALL,
            message:"hi",
            record:rec,
            language: VoiceLanguage.PORTUGUESE,
            media: new MediaInfo()
        )
        fMsg.save(flush:true, failOnError:true)

        when: "we marshal this message"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(fMsg as JSON)
        }

        then:
        validate(json, fMsg)
        json.endDate == null
        json.hasEndDate == null
        json.nextFireDate == null
    }

    void "test marshalling simple future message"() {
        given: "a nonrepeating simple future message"
        SimpleFutureMessage sMsg = new SimpleFutureMessage(
            type:FutureMessageType.CALL,
            message:"hi",
            record:rec,
            language: VoiceLanguage.ITALIAN,
            media: new MediaInfo()
        )
        sMsg.save(flush:true, failOnError:true)

        when: "we make this message repeating via repeatCount then marshal"
        sMsg.repeatCount = 10
        sMsg.save(flush:true, failOnError:true)
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(sMsg as JSON)
        }

        then:
        validate(json, sMsg)
        json.repeatIntervalInDays == sMsg.repeatIntervalInDays
        json.endDate == null
        json.hasEndDate == false
        json.repeatCount == sMsg.repeatCount
        json.uploadErrors == null

        when: "we make this message repeating via endDate then marshal"
        sMsg.repeatCount = null
        sMsg.endDate = DateTime.now().plusDays(1)
        sMsg.save(flush:true, failOnError:true)
        Helpers.trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, ["errors1", "errors2"])
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(sMsg as JSON)
        }

        then:
        validate(json, sMsg)
        json.repeatIntervalInDays == sMsg.repeatIntervalInDays
        json.endDate == sMsg.endDate.toString()
        json.hasEndDate == true
        json.repeatCount == null
        json.uploadErrors instanceof List
    }
}
