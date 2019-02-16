package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class RecordItemJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling record item"() {
        given:
        RecordItem rItem1 = TestUtils.buildRecordItem()
        rItem1.media = TestUtils.buildMediaInfo()
        rItem1.author = TestUtils.buildAuthor()
        rItem1.addReceipt(TestUtils.buildTempReceipt(ReceiptStatus.BUSY))

        RecordItem.withSession { it.flush() }

        when:
        Map json = TestUtils.objToJsonMap(rItem1)

        then:
        json.hasAwayMessage == rItem1.hasAwayMessage
        json.id == rItem1.id
        json.isAnnouncement == rItem1.isAnnouncement
        json.isDeleted == rItem1.isDeleted
        json.media instanceof Map
        json.outgoing == rItem1.outgoing
        json.receipts instanceof Map
        json.wasScheduled == rItem1.wasScheduled
        json.whenCreated == rItem1.whenCreated.toString()

        json.authorId == rItem1.authorId
        json.authorName == rItem1.authorName
        json.authorType == rItem1.authorType.toString()
        json.noteContents == rItem1.noteContents
    }

    void "test marshalling with different record owners"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(gpr1.record)

        when:
        RequestUtils.trySet(RequestUtils.PHONE_ID, "not a number")
        Map json = TestUtils.objToJsonMap(rItem1)

        then:
        json.contact == null
        json.tag == null

        when: "record owner is a contact"
        RequestUtils.trySet(RequestUtils.PHONE_ID, ipr1.phone.id)
        json = TestUtils.objToJsonMap(rItem1)

        then:
        json.contact == ipr1.id
        json.tag == null

        when: "record owner is a tag"
        RequestUtils.trySet(RequestUtils.PHONE_ID, gpr1.phone.id)
        json = TestUtils.objToJsonMap(rItem2)

        then:
        json.contact == null
        json.tag == gpr1.id
    }

    void "test marshalling call"() {
        given:
        RecordCall rCall1 = TestUtils.buildRecordCall()
        rCall1.voicemailInSeconds = TestUtils.randIntegerUpTo(88, true)
        RecordCall.withSession { it.flush() }

    	when:
    	Map json = TestUtils.objToJsonMap(rCall1)

    	then:
        json.durationInSeconds > 0
        json.durationInSeconds == rCall1.durationInSeconds
        json.voicemailInSeconds > 0
        json.voicemailInSeconds == json.voicemailInSeconds
        json.type == RecordItemType.CALL.toString()
    }

    void "test marshalling text"() {
        given:
        RecordText rText1 = TestUtils.buildRecordText()

        when:
        Map json = TestUtils.objToJsonMap(rText1)

        then:
        json.contents == rText1.contents
        json.type == RecordItemType.TEXT.toString()
    }

    void "test marshalling note with revisions, location, images, upload links"() {
        given: "note with revisions, location, images, upload links"
        RecordNote rNote1 = TestUtils.buildRecordNote()

        when:
        Map json = TestUtils.objToJsonMap(rNote1)

        then:
        json.isReadOnly == rNote1.isReadOnly
        json.revisions == null
        json.location instanceof Map
        json.type == RecordItemType.NOTE.toString()
        json.whenChanged == rNote1.whenChanged.toString()

        when:
        rNote1.authorName = TestUtils.randString()
        rNote1.tryCreateRevision()
        rNote1.save(flush: true, failOnError: true)

        json = TestUtils.objToJsonMap(rNote1)

        then:
        json.revisions instanceof Collection
        json.revisions.size() == 1
    }

    void "test marshalling note with specified timezone"() {
        given:
        RecordNote rNote1 = TestUtils.buildRecordNote()

        when:
        RequestUtils.trySet(RequestUtils.TIMEZONE, 1234)
        Map json = TestUtils.objToJsonMap(rNote1)

        then:
        json.whenCreated.contains("Z")
        json.whenChanged.contains("Z")

        when:
        RequestUtils.trySet(RequestUtils.TIMEZONE, "Europe/Stockholm")
        json = TestUtils.objToJsonMap(rNote1)

        then:
        json.whenCreated.contains("+01:00")
        json.whenChanged.contains("+01:00")
    }
}
