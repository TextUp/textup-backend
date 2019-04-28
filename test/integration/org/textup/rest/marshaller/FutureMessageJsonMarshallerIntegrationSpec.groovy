package org.textup.rest.marshaller

import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class FutureMessageJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling future message"() {
        given: "a nonrepeating future message"
        FutureMessage fMsg1 = new FutureMessage(type: FutureMessageType.CALL,
            message: TestUtils.randString(),
            record: TestUtils.buildRecord(),
            language: VoiceLanguage.values()[0],
            media: TestUtils.buildMediaInfo())
        fMsg1.save(flush:true, failOnError:true)

        when: "we marshal this message"
        Map json = TestUtils.objToJsonMap(fMsg1)

        then:
        json.id == fMsg1.id
        json.whenCreated == fMsg1.whenCreated.toString()
        json.startDate == fMsg1.startDate.toString()
        json.notifySelfOnSend == fMsg1.notifySelfOnSend
        json.type == fMsg1.type.toString()
        json.message == fMsg1.message
        json.isDone == fMsg1.isReallyDone
        json.isRepeating == fMsg1.isRepeating
        json.language == fMsg1.language.toString()
        json.media instanceof Map

        json.timesTriggered == null
        json.nextFireDate == null
        json.endDate == null
        json.hasEndDate == null
        json.nextFireDate == null
    }

    void "test marshalling with different owners"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        when:
        RequestUtils.trySet(RequestUtils.PHONE_RECORD_ID, "not a number")
        Map json = TestUtils.objToJsonMap(fMsg1)

        then:
        json.tag == null
        json.contact == null

        when:
        RequestUtils.trySet(RequestUtils.PHONE_RECORD_ID, ipr1.id)
        json = TestUtils.objToJsonMap(fMsg1)

        then:
        json.tag == null
        json.contact == ipr1.id

        when:
        RequestUtils.trySet(RequestUtils.PHONE_RECORD_ID, spr1.id)
        json = TestUtils.objToJsonMap(fMsg1)

        then:
        json.tag == null
        json.contact == spr1.id

        when:
        RequestUtils.trySet(RequestUtils.PHONE_RECORD_ID, gpr1.id)
        json = TestUtils.objToJsonMap(fMsg1)

        then:
        json.tag == gpr1.id
        json.contact == null
    }

    void "test marshalling simple future message"() {
        given:
        SimpleFutureMessage sMsg1 = TestUtils.buildFutureMessage()
        sMsg1.repeatCount = 10
        SimpleFutureMessage.withSession { it.flush() }

        when: "we make this message repeating via repeatCount then marshal"
        Map json = TestUtils.objToJsonMap(sMsg1)

        then:
        json.repeatIntervalInDays == sMsg1.repeatIntervalInDays
        json.endDate == null
        json.repeatCount == sMsg1.repeatCount

        when: "we make this message repeating via endDate then marshal"
        sMsg1.repeatCount = null
        sMsg1.endDate = DateTime.now().plusDays(1)
        sMsg1.save(flush: true, failOnError: true)

        json = TestUtils.objToJsonMap(sMsg1)

        then: "no upload errors here -- see MediaInfo json marshaller"
        json.repeatIntervalInDays == sMsg1.repeatIntervalInDays
        json.endDate == sMsg1.endDate.toString()
        json.repeatCount == null
    }
}
