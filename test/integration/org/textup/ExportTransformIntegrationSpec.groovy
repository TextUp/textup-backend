package org.textup

import groovy.util.slurpersupport.GPathResult
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

class ExportTransformIntegrationSpec extends CustomSpec {

    GrailsApplication grailsApplication
    AuthService authService

    def setup() {
        setupIntegrationData()
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    void "test transforming export for selected record owners"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        iReq.contacts.recipients << c1
        iReq.sharedContacts.recipients << sc2
        iReq.tags.recipients << tag1
        assert sc2.sharedWith.id == p1.id

        int numContacts = 1
        int numSharedContacts = 1
        int numTags = 1
        int numRecordOwners = numContacts + numSharedContacts + numTags

        when: "single stream"
        iReq.groupByEntity = false
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
        iReq.groupByEntity = true
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
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence".size() == 1
        xml."page-sequence"."static-content".text().contains("all records within")
        xml.text().contains("This export is for") == false
    }

    void "test displaying export context (phone + user names)"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        String phoneName = p1.name
        String phoneNumber = p1.number.prettyPhoneNumber
        String staffName = TestUtils.randString()
        MockedMethod getAuthName = TestUtils.mock(authService, "getLoggedInAndActive") { ->
            GroovyStub(Staff) { getName() >> staffName }
        }

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        getAuthName.callCount > 0
        xml."page-sequence"."static-content".each {
            assert it.text().contains(phoneName)
            assert it.text().contains(phoneNumber)
            assert it.text().contains(staffName)
        }

        cleanup:
        getAuthName.restore()
    }

    void "test displaying start and end dates"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)

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
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        int totalNum = Constants.MAX_PAGINATION_MAX * 2
        MockedMethod countRecordItems = TestUtils.mock(iReq, "countRecordItems") { totalNum }

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence"."static-content".each {
            assert it
                .text()
                .replaceAll(/\s+/, " ")
                .contains("Exported ${Constants.MAX_PAGINATION_MAX} (max allowed) of ${totalNum} total items")
        }

        cleanup:
        countRecordItems.restore()
    }

    void "test providing a count of types of record items contained in this export"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        Contact contact1 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        contact1.save(flush: true, failOnError: true)
        iReq.contacts.recipients << contact1

        int numCalls = 4
        int numTexts = 2
        int numNotes = 4
        int numItems = numCalls + numTexts + numNotes
        numTexts.times {
            contact1.record.storeOutgoingText(TestUtils.randString())
        }
        numCalls.times {
            contact1.record.storeOutgoingCall()
        }
        numNotes.times {
            new RecordNote(record: contact1.record, noteContents: TestUtils.randString()).save()
        }
        Contact.withSession { it.flush() }

        when:
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
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        iReq.contacts.recipients << c1
        iReq.sharedContacts.recipients << sc2
        iReq.tags.recipients << tag1
        assert sc2.sharedWith.id == p1.id

        // create incoming call
        sc2.contact.record.storeOutgoingCall().payload.outgoing = false
        SharedContact.withSession { it.flush() }

        String sharedContactName = sc2.name
        String sharedByPhoneNumber = sc2.sharedBy.number.prettyPhoneNumber

        when: "in the same section as other record owners"
        iReq.groupByEntity = false
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence".size() == 1
        xml."page-sequence".text().contains(sharedByPhoneNumber) == false

        when: "a single shared contact in a section alone WITH AN INCOMING CALL OR TEXT"
        iReq.groupByEntity = true
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        then:
        xml."page-sequence".size() > 1
        xml."page-sequence".each {
            // only the page sequence with the shared contact by itself contains
            // the shared by phone. Every other section has the current phone
            assert it.text().contains(sharedContactName) == it.text().contains(sharedByPhoneNumber)
        }
    }

    void "test displaying incoming vs outgoing record item"() {
        given:
        String ownerName = TestUtils.randString()
        String authorName = TestUtils.randString()
        String phoneName = p1.name
        String phoneNumber = p1.number.prettyPhoneNumber

        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        Contact contact1 = p1.createContact([name: ownerName], [TestUtils.randPhoneNumber()]).payload
        contact1.save(flush: true, failOnError: true)
        iReq.contacts.recipients << contact1

        RecordItem rItem = contact1.record.storeOutgoingCall().payload
        Contact.withSession { it.flush() }

        when: "incoming"
        rItem.outgoing = false
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("From ${ownerName}")
        outputString.contains("To ${phoneName} (${phoneNumber})")

        when: "outgoing without author"
        rItem.outgoing = true
        rItem.authorName = null
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("From not recorded")
        outputString.contains("To ${ownerName}")

        when: "outgoing with author"
        rItem.outgoing = true
        rItem.authorName = authorName
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("From ${authorName}")
        outputString.contains("To ${ownerName}")
    }

    void "testing displaying media"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        Contact contact1 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        contact1.save(flush: true, failOnError: true)
        iReq.contacts.recipients << contact1

        RecordItem rItem = contact1.record.storeOutgoingCall().payload
        Contact.withSession { it.flush() }

        MediaInfo mInfo = new MediaInfo()
        int numAudio = 2
        int numImages = 3
        numAudio.times {
            MediaElement el1 = TestUtils.buildMediaElement()
            el1.sendVersion.type = MediaType.AUDIO_MP3
            mInfo.addToMediaElements(el1)
        }
        numImages.times {
            MediaElement el1 = TestUtils.buildMediaElement()
            el1.sendVersion.type = MediaType.IMAGE_PNG
            mInfo.addToMediaElements(el1)
        }

        when: "no media"
        rItem.media = null
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("${numAudio} audio recordings") == false
        outputString.contains("${numImages} images") == false

        when: "has media"
        rItem.media = mInfo
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("${numAudio} audio recordings")
        outputString.contains("${numImages} images")
    }

    void "test displaying receipts"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        Contact contact1 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        contact1.save(flush: true, failOnError: true)
        iReq.contacts.recipients << contact1

        RecordItem rItem = contact1.record.storeOutgoingCall().payload
        2.times { rItem.addReceipt(TestUtils.buildTempReceipt(ReceiptStatus.SUCCESS)) }
        4.times { rItem.addReceipt(TestUtils.buildTempReceipt(ReceiptStatus.BUSY)) }
        Contact.withSession { it.flush() }

        Collection<String> successNums = rItem.groupReceiptsByStatus().success
        Collection<String> busyNums = rItem.groupReceiptsByStatus().busy

        when: "incoming"
        rItem.outgoing = false
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then: "no receipts displayed, even if present"
        outputString.contains("This export contains 1 record items")
        outputString.contains("Successfully received by ${successNums.size()}") == false
        outputString.contains("Pending for") == false
        outputString.contains("Busy for ${busyNums.size()}") == false
        outputString.contains("Failed for") == false

        when: "outgoing"
        rItem.outgoing = true
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
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        Contact contact1 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        contact1.save(flush: true, failOnError: true)
        iReq.contacts.recipients << contact1

        RecordNote rNote1 = new RecordNote(record: contact1.record,
            noteContents: TestUtils.randString(),
            location: TestUtils.buildLocation(),
            media: new MediaInfo()).save()
        Contact.withSession { it.flush() }

        String noteContents = rNote1.noteContents
        String locAddress = rNote1.location.address

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("Note: ${noteContents}")
        outputString.contains("Attachments:")
        outputString.contains("Location: ${locAddress}")
    }

    void "test displaying text"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        Contact contact1 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        contact1.save(flush: true, failOnError: true)
        iReq.contacts.recipients << contact1

        String textContents = TestUtils.randString()
        contact1.record.storeOutgoingText(textContents)
        contact1.withSession { it.flush() }

        when:
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("Contents: ${textContents}")
        outputString.contains("Attachments:") == false
        outputString.contains("Location:") == false
    }

    void "test displaying call"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        Contact contact1 = p1.createContact([:], [TestUtils.randPhoneNumber()]).payload
        contact1.save(flush: true, failOnError: true)
        iReq.contacts.recipients << contact1

        String noteContents = TestUtils.randString()
        int voicemailSeconds = TestUtils.randIntegerUpTo(88)
        int callSeconds = TestUtils.randIntegerUpTo(88)

        RecordCall rCall1 = contact1.record.storeOutgoingCall().payload
        rCall1.outgoing = false
        rCall1.noteContents = noteContents
        TempRecordReceipt r1 = TestUtils.buildTempReceipt()
        r1.numSegments = callSeconds
        rCall1.addReceipt(r1)
        contact1.withSession { it.flush() }

        when: "voicemail"
        rCall1.voicemailInSeconds = voicemailSeconds
        rCall1.save(flush: true, failOnError: true)
        GPathResult xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        String outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("Note: ${noteContents}")
        outputString.contains("${voicemailSeconds} second voicemail message")
        outputString.contains("phone call") == false

        when: "call"
        rCall1.voicemailInSeconds = 0
        rCall1.save(flush: true, failOnError: true)
        xml = TestUtils.buildXmlTransformOutput(grailsApplication, iReq)

        outputString = xml.text().replaceAll(/\s+/, " ")

        then:
        outputString.contains("This export contains 1 record items")
        outputString.contains("Note: ${noteContents}")
        outputString.contains("voicemail message") == false
        outputString.contains("${callSeconds} second phone call")
    }
}
