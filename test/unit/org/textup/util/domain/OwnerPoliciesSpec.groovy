package org.textup.util.domain

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class OwnerPoliciesSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding or creating for `PhoneOwnership` and staff id"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildStaffPhone()
        int opBaseline = OwnerPolicy.count()

        when:
        Result res = OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(null, null)

        then:
        res.status == ResultStatus.NOT_FOUND
        OwnerPolicy.count() == opBaseline

        when:
        res = OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof OwnerPolicy
        res.payload.staff == s1
        res.payload.schedule instanceof Schedule
        OwnerPolicy.count() == opBaseline + 1

        when:
        Result res2 = OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)

        then:
        res2.status == ResultStatus.OK
        res2.payload == res.payload
        OwnerPolicy.count() == opBaseline + 1
    }

    void "test finding readonly or default policy for `PhoneOwnership` and `Staff`"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Staff s2 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildStaffPhone()
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy(p1.owner, s2)
        int opBaseline = OwnerPolicy.count()

        when:
        def retVal = OwnerPolicies.findReadOnlyOrDefaultForOwnerAndStaff(null, null)

        then:
        retVal instanceof DefaultOwnerPolicy
        retVal.readOnlyStaff == null
        retVal.readOnlySchedule != null

        when:
        retVal = OwnerPolicies.findReadOnlyOrDefaultForOwnerAndStaff(p1.owner, s1)

        then:
        retVal instanceof DefaultOwnerPolicy
        retVal.readOnlyStaff == s1
        retVal.readOnlySchedule != null

        when:
        retVal = OwnerPolicies.findReadOnlyOrDefaultForOwnerAndStaff(p1.owner, s2)

        then:
        retVal instanceof OwnerPolicy
        retVal == op1
    }
}
