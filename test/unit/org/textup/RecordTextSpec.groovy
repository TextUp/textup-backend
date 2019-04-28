package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordTextSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test static creation"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        String contents = TestUtils.randString()

        when:
        Result res = RecordText.tryCreate(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = RecordText.tryCreate(rec1, contents)

        then:
        res.status == ResultStatus.CREATED
        res.payload.record == rec1
        res.payload.contents == contents
    }

    void "test contents length constraint"() {
    	when: "we have a record text"
    	Record rec = TestUtils.buildRecord()
    	RecordText rText = new RecordText(record:rec, contents:"hi")

    	then:
    	rText.validate() == true

    	when: "we add a too-short contents"
    	rText.contents = ""

    	then: "we don't have to contents because the message may just contain media \
            OutgoingMessage validator enforces this"
    	rText.validate() == true

    	when: "we add contents longer than two text message lengths"
    	rText.contents = '''
			Far far away, behind the word mountains, far from the countries Vokalia and
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove
			right at the coast of the Semantics, a large language ocean. A small river
			named Duden flows by their place and supplies it with the necessary regelialia.
			It is a paradisemati
		'''

    	then: "valid, removed length constraint to avoid rejecting incoming msgs"
    	rText.validate() == true

        when: "we add contents longer than supported by text column type"
        rText.noteContents = TestUtils.buildVeryLongString()

        then: "shared contraint on the noteContents field is executed"
        rText.validate() == false
        rText.errors.getFieldErrorCount("noteContents") == 1
    }

    void "test calculating number of segments"() {
        when: "no receipts"
        Record rec = TestUtils.buildRecord()
        RecordText rText = new RecordText(record:rec, contents:"hi")
        assert rText.validate()

        then: "no segments"
        rText.receipts == null
        rText.numSegments == 0

        when: "some receipts all without segments"
        TempRecordReceipt temp1 = TestUtils.buildTempReceipt()
        temp1.numBillable = null
        rText.addReceipt(temp1)

        then: "no segments"
        rText.receipts.size() == 1
        rText.numSegments == 0

        when: "some receipts with segments"
        TempRecordReceipt temp2 = TestUtils.buildTempReceipt()
        temp2.numBillable = 88
        rText.addReceipt(temp2)

        then: "has number of segments greater than 0"
        rText.receipts.size() == 2
        rText.numSegments > 0
    }
}
