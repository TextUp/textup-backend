package org.textup.util

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class DuplicateUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding ambiguous ids"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        Long id1 = 1
        Long id2 = 2
        Long id3 = 3
        Long id4 = 4
        Map numToIdsNoAmbiguous = [(pNum1): [id1, id2], (pNum2): [id3, id4]]
        Map numToIdsWithAmbiguous = [(pNum1): [id1, id2, id3], (pNum2): [id1, id3, id4]]

        when:
        HashSet ambiguousIds = DuplicateUtils.findAmbiguousIds(null)

        then:
        ambiguousIds.isEmpty()

        when:
        ambiguousIds = DuplicateUtils.findAmbiguousIds(numToIdsNoAmbiguous)

        then:
        ambiguousIds.isEmpty()

        when:
        ambiguousIds = DuplicateUtils.findAmbiguousIds(numToIdsWithAmbiguous)

        then:
        ambiguousIds.size() == 2
        id1 in ambiguousIds
        id3 in ambiguousIds
    }

    void "test finding target id to merge others into"() {
        given:
        Long id1 = 1
        Long id2 = 2
        Long id3 = 3
        Long id4 = 4
        HashSet ambiguousIds = [id1, id2]
        Collection possibleIdsNoAmbig = [id3, id4]
        Collection possibleIdsOneAmbig = [id1, id3, id4]
        Collection possibleIdsMultipleAmbig = [id1, id2, id3, id4]

        when:
        Long targetId = DuplicateUtils.findMergeTargetId(null, null)

        then:
        targetId == null

        when:
        targetId = DuplicateUtils.findMergeTargetId(possibleIdsNoAmbig, ambiguousIds)

        then:
        targetId in possibleIdsNoAmbig

        when:
        targetId = DuplicateUtils.findMergeTargetId(possibleIdsOneAmbig, ambiguousIds)

        then:
        targetId == possibleIdsOneAmbig.intersect(ambiguousIds)[0]

        when:
        targetId = DuplicateUtils.findMergeTargetId(possibleIdsMultipleAmbig, ambiguousIds)

        then:
        targetId == null
    }

    void "test building merge groups overall"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        Long id1 = TestUtils.buildIndPhoneRecord().id
        Long id2 = TestUtils.buildIndPhoneRecord().id
        Long id3 = TestUtils.buildIndPhoneRecord().id
        Long id4 = TestUtils.buildIndPhoneRecord().id
        Map numToIdsNoAmbiguous = [(pNum1): [id1, id2], (pNum2): [id3, id4]]
        Map numToIdsWithOneAmbiguous = [(pNum1): [id1, id2], (pNum2): [id1, id3, id4]]
        Map numToIdsWithManyAmbiguous = [(pNum1): [id1, id2, id3], (pNum2): [id1, id3, id4]]

        when:
        ResultGroup resGroup = DuplicateUtils.tryBuildMergeGroups(null)

        then:
        resGroup.isEmpty

        when:
        resGroup = DuplicateUtils.tryBuildMergeGroups(numToIdsNoAmbiguous)

        then: "no overlaps to merge"
        resGroup.isEmpty

        when:
        resGroup = DuplicateUtils.tryBuildMergeGroups(numToIdsWithManyAmbiguous)

        then: "too many overlaps (ambiguous ids) to merge, max allowed is one per group"
        resGroup.isEmpty

        when:
        resGroup = DuplicateUtils.tryBuildMergeGroups(numToIdsWithOneAmbiguous)

        then:
        resGroup.anyFailures == false
        resGroup.anySuccesses == true
        resGroup.payload.size() == 1
        resGroup.payload[0] instanceof MergeGroup
        resGroup.payload[0].targetId == id1
        resGroup.payload[0].possibleMerges.size() == 2
        resGroup.payload[0].possibleMerges.find {
            it.number == pNum1 && it.mergeIds.size() == 1 && id2 in it.mergeIds
        }
        resGroup.payload[0].possibleMerges.find {
            it.number == pNum2 && it.mergeIds.size() == 2 && id3 in it.mergeIds && id4 in it.mergeIds
        }
    }
}
