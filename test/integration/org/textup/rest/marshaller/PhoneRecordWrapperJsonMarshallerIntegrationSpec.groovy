package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class PhoneRecordWrapperJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling errors"() {
        given:
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE

        PhoneRecord.withSession { it.flush() }

        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when:
        Map json = TestUtils.objToJsonMap(gpr1.toWrapper())

        then:
        stdErr.toString().contains("is not an `IndividualPhoneRecordWrapper`")
        json == [:]

        when:
        json = TestUtils.objToJsonMap(spr1.toWrapper())

        then:
        stdErr.toString().contains("cannot be viewed")
        json == [:]

        cleanup:
        TestUtils.restoreAllStreams()
    }

    void "test marshalling wrapped owned contact"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = PhoneRecordStatus.ACTIVE
        PhoneRecord.withSession { it.flush() }

        when:
        Map json = TestUtils.objToJsonMap(ipr1.toWrapper())

        then:
        json.futureMessages instanceof Collection
        json.futureMessages.isEmpty()
        json.id == ipr1.id
        json.language == ipr1.record.language.toString()
        json.lastRecordActivity == ipr1.record.lastRecordActivity.toString()
        json.name == ipr1.name
        json.note == ipr1.note
        json.numbers instanceof Collection
        json.numbers.size() == ipr1.sortedNumbers.size()
        json.phone == ipr1.phone.id
        json.status == ipr1.status.toString()
        json.tags instanceof Collection
        json.tags.isEmpty()
        json.whenCreated == ipr1.whenCreated.toString()
        json.unreadInfo == null
        json.permission == null
        json.sharedByName == null
        json.sharedByPhoneId == null
        json.sharedWith instanceof Collection
        json.sharedWith.isEmpty()

        when: "is unread"
        ipr1.status = PhoneRecordStatus.UNREAD
        ipr1.save(flush: true, failOnError: true)
        json = TestUtils.objToJsonMap(ipr1.toWrapper())

        then:
        json.unreadInfo instanceof Map
        json.unreadInfo.numTexts >= 0
        json.unreadInfo.numCalls >= 0
        json.unreadInfo.numVoicemails >= 0

        when: "is shared with someone"
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        json = TestUtils.objToJsonMap(ipr1.toWrapper())

        then:
        json.sharedWith instanceof Collection
        json.sharedWith.size() == 1
        json.sharedWith[0].whenCreated == spr1.whenCreated.toString()
        json.sharedWith[0].phoneId == spr1.phone.id
        json.sharedWith[0].permission == spr1.permission.toString()
    }

    void "test marshalling wrapped shared contact"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)

        when:
        Map json = TestUtils.objToJsonMap(spr1.toWrapper())

        then:
        json.id == spr1.id
        json.status == spr1.status.toString()
        json.phone == spr1.phone.id
        json.permission == spr1.permission.toString()
        json.sharedByName == ipr1.phone.buildName()
        json.sharedByPhoneId == ipr1.phone.id
    }

    void "test should only return not-done future messages"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        FutureMessage fMsg1 = TestUtils.buildFutureMessage(ipr1.record)
        fMsg1.isDone = false
        FutureMessage fMsg2 = TestUtils.buildFutureMessage(ipr1.record)
        fMsg2.isDone = true
        FutureMessage.withSession { it.flush() }

        when:
        Map json = TestUtils.objToJsonMap(ipr1.toWrapper())

        then:
        json.id == ipr1.id
        json.futureMessages instanceof Collection
        json.futureMessages.size() == 1
        json.futureMessages[0].id == fMsg1.id
    }
}
