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
class OrganizationSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        given:
        int baseline = Organization.count()
        String name1 = TestUtils.randString()
        String name2 = TestUtils.randString()
        Location invalidLoc = new Location()
        Location loc1 = TestUtils.buildLocation()
        Location loc2 = TestUtils.buildLocation()

        when:
        Result res = Organization.tryCreate(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Organization.tryCreate(name1, invalidLoc)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Organization.tryCreate(name1, loc1)
        Organization.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        res.payload.name == name1
        res.payload.location == loc1
        Organization.count() == baseline + 1

        when:
        res = Organization.tryCreate(name1, loc1)
        Organization.withSession { it.flush() }

        then: "duplicate"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Organization.tryCreate(name2, loc1)
        Organization.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        res.payload.name == name2
        res.payload.location == loc1
        Organization.count() == baseline + 2

        when:
        res = Organization.tryCreate(name1, loc2)
        Organization.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        res.payload.name == name1
        res.payload.location == loc2
        Organization.count() == baseline + 3
    }

    void "test away message suffix constraints"() {
        given:
        Organization org1 = TestUtils.buildOrg()

        when: "away message suffix is null"
        org1.awayMessageSuffix = null

        then:
        org1.validate()

        when: "away message suffix is absent"
        org1.awayMessageSuffix = ""

        then:
        org1.validate()

        when: "away message suffix is too long"
        org1.awayMessageSuffix = TestUtils.buildVeryLongString()

        then:
        org1.validate() == false
        org1.errors.getFieldErrorCount("awayMessageSuffix")

        when: "away message suffix is present and within length constraints"
        org1.awayMessageSuffix = TestUtils.randString()

        then:
        org1.validate()
    }
}
