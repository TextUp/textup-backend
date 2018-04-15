package org.textup.rest.marshaller

import grails.converters.JSON
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.FutureMessageType
import org.textup.type.VoiceLanguage
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
        assert json.isRepeating == fMsg.isRepeating
        assert json.language == fMsg.language.toString()
        // always will be null because we aren't actually scheduling
        assert json.timesTriggered == null
        assert json.nextFireDate == null
        assert json.contact != null || json.tag != null
        if (json.contact) {
            assert Contact.exists(json.contact)
        }
        else if (json.tag) {
            assert ContactTag.exists(json.tag)
        }
        true
    }

    void "test marshalling future message"() {
        given: "a nonrepeating future message"
        FutureMessage fMsg = new FutureMessage(
            type:FutureMessageType.CALL,
            message:"hi",
            record:c1.record,
            language: VoiceLanguage.PORTUGUESE
        )
        fMsg.save(flush:true, failOnError:true)

        when: "we marshal this message"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(fMsg as JSON) as Map
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
            record:c1.record,
            language: VoiceLanguage.ITALIAN
        )
        sMsg.save(flush:true, failOnError:true)

        when: "we make this message repeating via repeatCount then marshal"
        sMsg.repeatCount = 10
        sMsg.save(flush:true, failOnError:true)
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(sMsg as JSON) as Map
        }

        then:
        validate(json, sMsg)
        json.repeatIntervalInDays == sMsg.repeatIntervalInDays
        json.endDate == null
        json.hasEndDate == false
        json.repeatCount == sMsg.repeatCount

        when: "we make this message repeating via endDate then marshal"
        sMsg.repeatCount = null
        sMsg.endDate = DateTime.now().plusDays(1)
        sMsg.save(flush:true, failOnError:true)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(sMsg as JSON) as Map
        }

        then:
        validate(json, sMsg)
        json.repeatIntervalInDays == sMsg.repeatIntervalInDays
        json.endDate == sMsg.endDate.toString()
        json.hasEndDate == true
        json.repeatCount == null
    }
}
