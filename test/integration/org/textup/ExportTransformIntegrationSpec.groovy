package org.textup

import grails.gorm.DetachedCriteria
import grails.test.runtime.*
import groovy.util.slurpersupport.GPathResult
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class ExportTransformIntegrationSpec extends Specification {

    GrailsApplication grailsApplication

    // [NOTE] for some reason, the data relationships don't persist properly unless this test is first
    void "test displaying incoming vs outgoing record item"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        RecordCall rCall1 = TestUtils.buildRecordCall(spr1.record)

        RecordItemRequest iReq = RecordItemRequest.tryCreate(p1, [spr1.toWrapper()], false).payload

        when: "incoming"
        rCall1.outgoing = false
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("From ${spr1.shareSource.secureName}")
        outputString.contains("To ${p1.buildName()}")
        outputString.contains(p1.number.prettyPhoneNumber)

        when: "outgoing without author"
        rCall1.outgoing = true
        rCall1.authorName = null
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("From not recorded")
        outputString.contains("To ${spr1.shareSource.secureName}")

        when: "outgoing with author"
        rCall1.outgoing = true
        rCall1.authorName = TestUtils.randString()
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("From ${rCall1.authorName}")
        outputString.contains("To ${spr1.shareSource.secureName}")
    }

    void "test transforming export for selected record owners"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)

        int numContacts = 1
        int numSharedContacts = 1
        int numTags = 1
        int numRecordOwners = numContacts + numSharedContacts + numTags

        when: "single stream"
        RecordItemRequest iReq = RecordItemRequest
            .tryCreate(p1, [ipr1, gpr1, spr1]*.toWrapper(), false)
            .payload
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence".size() == 1
        xml.text().contains("This export is for")
        String outputString = xml."page-sequence"."static-content".text().replaceAll(/\s+/, " ")
        // do not say all records within phone because we are showing selected records
        outputString.contains("all records within") == false
        // if showing all selected in one section, provide counts of record owners
        outputString.contains("${numContacts} contacts")
        outputString.contains("${numSharedContacts} shared contacts")
        outputString.contains("${numTags} tags")

        when: "grouped by entity"
        iReq = RecordItemRequest
            .tryCreate(p1, [ipr1, gpr1, spr1]*.toWrapper(), true)
            .payload
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence".size() == numRecordOwners
        xml."page-sequence".each {
            assert it.text().contains("This export is for")
            // do not say all records within phone because we are showing selected records
            assert it."static-content".text().contains("all records within") == false
            // each section contains only one and keeps track of number of entities remaining
            assert it."static-content".text().contains("of ${numRecordOwners})")
        }
    }

    void "test transforming export for all records within a phone"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        RecordItemRequest iReq = RecordItemRequest.tryCreate(p1, null, false).payload

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence".size() == 1
        xml."page-sequence"."static-content".text().contains("all records within")
        xml.text().contains("This export is for") == false
    }

    void "test displaying export context (phone + user names)"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone(s1)
        RecordItemRequest iReq = RecordItemRequest.tryCreate(p1, null, false).payload
        // in `RecordItemRequestJsonMarshaller`
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") { ->
            GroovyStub(Staff) { getName() >> s1.name }
        }

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        tryGetActiveAuthUser.hasBeenCalled
        xml."page-sequence"."static-content".each {
            assert it.text().contains(p1.buildName())
            assert it.text().contains(p1.number.prettyPhoneNumber)
            assert it.text().contains(s1.name)
        }

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test displaying start and end dates"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        RecordItemRequest iReq = RecordItemRequest.tryCreate(p1, null, false).payload

        when: "no dates specified"
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence"."static-content".each {
            assert it.text().contains("beginning")
            assert it.text().contains("end")
        }

        when: "one date specified"
        iReq.start = DateTime.now().minusDays(8)
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence"."static-content".each {
            assert it.text().contains("beginning") == false
            assert it.text().contains("end")
        }

        when: "both dates specified"
        iReq.start = DateTime.now().minusDays(8)
        iReq.end = DateTime.now().minusDays(2)
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence"."static-content".each {
            assert it.text().contains("beginning") == false
            assert it.text().contains("end") == false
        }
    }

    void "test handling when number of items exceeds the max allowed to export at one time"() {
        given:
        int totalNum = ControllerUtils.MAX_PAGINATION_MAX * 2

        Phone p1 = TestUtils.buildActiveStaffPhone()
        RecordItemRequest iReq = RecordItemRequest.tryCreate(p1, null, false).payload

        DetachedCriteria crit1 = GroovyMock()
        MockedMethod getCriteria = MockedMethod.create(iReq, "getCriteria") { crit1 }

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        (1.._) * crit1.build(*_) >> crit1
        (1.._) * crit1.count() >> totalNum
        (1.._) * crit1.list(*_) >> []
        xml."page-sequence"."static-content".each {
            assert it
                .text()
                .replaceAll(/\s+/, " ")
                .contains("Exported ${ControllerUtils.MAX_PAGINATION_MAX} (max allowed) of ${totalNum} total items")
        }

        cleanup:
        getCriteria?.restore()
    }

    void "test providing a count of types of record items contained in this export"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)

        int numTexts = 2
        numTexts.times { TestUtils.buildRecordText(ipr1.record) }
        int numCalls = 4
        numCalls.times { TestUtils.buildRecordCall(ipr1.record) }
        int numNotes = 4
        numNotes.times { TestUtils.buildRecordNote(ipr1.record) }
        int numItems = numCalls + numTexts + numNotes

        when:
        RecordItemRequest iReq = RecordItemRequest.tryCreate(p1, [ipr1.toWrapper()], false).payload
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence"."flow".each {
            String outputString = it.text().replaceAll(/\s+/, " ")
            assert outputString.contains("${numCalls} calls")
            assert outputString.contains("${numTexts} texts")
            assert outputString.contains("${numNotes} notes")
            assert outputString.contains("This export contains ${numItems} record items")
        }
    }

    void "test displaying shared contact"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)

        RecordCall rCall1 = TestUtils.buildRecordCall(spr1.record)
        rCall1.outgoing = false
        RecordCall.withSession { it.flush() }

        String sharedContactName = spr1.shareSource.name
        String sharedByPhoneNumber = spr1.shareSource.phone.number.prettyPhoneNumber

        when: "in the same section as other record owners"
        RecordItemRequest iReq = RecordItemRequest
            .tryCreate(p1, [ipr1, gpr1, spr1]*.toWrapper(), false)
            .payload
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence".size() == 1
        xml."page-sequence".text().contains(sharedByPhoneNumber) == false

        when: "a single shared contact in a section alone WITH AN INCOMING CALL OR TEXT"
        iReq = RecordItemRequest
            .tryCreate(p1, [ipr1, gpr1, spr1]*.toWrapper(), true)
            .payload
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence".size() > 1
        xml."page-sequence".each {
            // only the page sequence with the shared contact by itself contains
            // the shared by phone. Every other section has the current phone
            assert it.text().contains(sharedContactName) == it.text().contains(sharedByPhoneNumber)
        }
    }

    void "testing displaying media"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        RecordCall rCall1 = TestUtils.buildRecordCall(gpr1.record)
        rCall1.media = null

        MediaInfo mInfo1 = TestUtils.buildMediaInfo()
        int numAudio = 2
        numAudio.times {
            MediaElement el1 = TestUtils.buildMediaElement()
            el1.sendVersion.type = MediaType.AUDIO_MP3
            mInfo1.addToMediaElements(el1)
        }
        int numImages = 3
        numImages.times {
            MediaElement el1 = TestUtils.buildMediaElement()
            el1.sendVersion.type = MediaType.IMAGE_PNG
            mInfo1.addToMediaElements(el1)
        }

        RecordItemRequest iReq = RecordItemRequest.tryCreate(p1, null, false).payload

        when: "no media"
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)
        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("${numAudio} audio recordings") == false
        outputString.contains("${numImages} images") == false

        when: "has media"
        rCall1.media = mInfo1
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("${numAudio} audio recordings")
        outputString.contains("${numImages} images")
    }

    void "test displaying receipts"() {
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        RecordCall rCall1 = TestUtils.buildRecordCall(ipr1.record)
        2.times { rCall1.addReceipt(TestUtils.buildTempReceipt(ReceiptStatus.SUCCESS)) }
        4.times { rCall1.addReceipt(TestUtils.buildTempReceipt(ReceiptStatus.BUSY)) }

        Collection successNums = rCall1.groupReceiptsByStatus().success
        Collection busyNums = rCall1.groupReceiptsByStatus().busy

        RecordItemRequest iReq = RecordItemRequest.tryCreate(tp1, null, false).payload

        when: "incoming"
        rCall1.outgoing = false
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then: "no receipts displayed, even if present"
        outputString.contains("This export contains 1 record items")
        outputString.contains("Successfully received by ${successNums.size()}") == false
        outputString.contains("Pending for") == false
        outputString.contains("Busy for ${busyNums.size()}") == false
        outputString.contains("Failed for") == false

        when: "outgoing"
        rCall1.outgoing = true
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then: "receipts displayed"
        outputString.contains("This export contains 1 record items")
        outputString.contains("Successfully received by ${successNums.size()}")
        successNums.every { outputString.contains(it) }
        outputString.contains("Pending for") == false
        outputString.contains("Busy for ${busyNums.size()}")
        busyNums.every { outputString.contains(it) }
        outputString.contains("Failed for") == false
    }

    void "test displaying note"() {
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        RecordNote rNote1 = TestUtils.buildRecordNote(ipr1.record)

        RecordItemRequest iReq = RecordItemRequest.tryCreate(tp1, null, false).payload
        String noteContents = rNote1.noteContents
        String locAddress = rNote1.location.address

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("Internal note")
        outputString.contains("This export contains 1 record items")
        outputString.contains("Note: ${noteContents}")
        outputString.contains("Attachments:")
        outputString.contains("Location: ${locAddress}")
    }

    void "test displaying note with only location"(){
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        RecordNote rNote1 = RecordNote.tryCreate(ipr1.record, null, null, TestUtils.buildLocation()).payload

        RecordItemRequest iReq = RecordItemRequest.tryCreate(tp1, null, false).payload
        String locAddress = rNote1.location.address

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("Internal note")
        outputString.contains("This export contains 1 record items")
        !outputString.contains("Note:")
        !outputString.contains("Attachments:")
        outputString.contains("Location: ${locAddress}")
    }

    void "test displaying text"() {
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        RecordText rText1 = TestUtils.buildRecordText(ipr1.record)

        RecordItemRequest iReq = RecordItemRequest.tryCreate(tp1, null, false).payload

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("Contents: ${rText1.contents}")
        outputString.contains("Attachments:") == false
        outputString.contains("Location:") == false
    }

    void "test displaying call"() {
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(tp1)
        RecordCall rCall1 = TestUtils.buildRecordCall(ipr1.record)
        rCall1.noteContents = TestUtils.randString()

        RecordItemRequest iReq = RecordItemRequest.tryCreate(tp1, null, false).payload
        int voicemailSeconds = TestUtils.randIntegerUpTo(88)

        when: "voicemail"
        rCall1.voicemailInSeconds = voicemailSeconds
        rCall1.save(flush: true, failOnError: true)
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("Note: ${rCall1.noteContents}")
        outputString.contains("${voicemailSeconds} second voicemail message")
        outputString.contains("phone call") == false

        when: "call"
        rCall1.voicemailInSeconds = 0
        rCall1.save(flush: true, failOnError: true)
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("Note: ${rCall1.noteContents}")
        outputString.contains("voicemail message") == false
        outputString.contains("${rCall1.durationInSeconds} second phone call")
    }
}
