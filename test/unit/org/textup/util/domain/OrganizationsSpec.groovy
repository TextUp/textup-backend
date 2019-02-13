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
class OrganizationsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding for id"() {
        given:
        Organization org1 = TestUtils.buildOrg()

        when:
        Result res = Organizations.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Organizations.mustFindForId(-88L)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Organizations.mustFindForId(org1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == org1
    }

    void "test checking if staff is admin at org"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        s1.status = StaffStatus.STAFF
        Staff s2 = TestUtils.buildStaff()
        s2.status = StaffStatus.ADMIN

        when:
        Result res = Organizations.tryIfAdmin(null, null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = Organizations.tryIfAdmin(s1.org.id, s1.id)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = Organizations.tryIfAdmin(s2.org.id, s2.id)

        then:
        res.status == ResultStatus.NO_CONTENT
    }

    void "test is allowed"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Staff s1 = TestUtils.buildStaff(org1)
        s1.status = StaffStatus.ADMIN
        Staff s2 = TestUtils.buildStaff(org1)
        s2.status = StaffStatus.STAFF

        MockedMethod tryGetAuthId = TestUtils.mock(AuthUtils, "tryGetAuthId") { Result.createSuccess(s1.id) }

        when:
        Result res = Organizations.isAllowed(null)

        then:
        tryGetAuthId.callCount == 1
        res.status == ResultStatus.FORBIDDEN

        when:
        res = Organizations.isAllowed(org1.id)

        then:
        tryGetAuthId.callCount == 2
        res.status == ResultStatus.OK
        res.payload == org1.id

        when:
        tryGetAuthId.restore()
        tryGetAuthId = TestUtils.mock(AuthUtils, "tryGetAuthId") { Result.createSuccess(s2.id) }

        res = Organizations.isAllowed(org1.id)

        then:
        tryGetAuthId.callCount == 1
        res.status == ResultStatus.FORBIDDEN

        cleanup:
        tryGetAuthId.restore()
    }

    void "test criteria given query and statuses"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        org1.status = OrgStatus.APPROVED

        Organization.withSession { it.flush() }

        when:
        DetachedCriteria criteria = Organizations.buildForOptions()

        then:
        criteria.count() > 0

        when:
        criteria = Organizations.buildForOptions(org1.name)

        then:
        criteria.list() == [org1]

        when:
        criteria = Organizations.buildForOptions(org1.location.address)

        then:
        criteria.list() == [org1]

        when:
        criteria = Organizations.buildForOptions(org1.location.address, [org1.status])

        then:
        criteria.list() == [org1]

        when:
        criteria = Organizations.buildForOptions(org1.location.address, [OrgStatus.REJECTED])

        then:
        criteria.count() == 0
    }

    void "test criteria given name and coordinates"() {
        given:
        Organization org1 = TestUtils.buildOrg()

        when:
        DetachedCriteria criteria = Organizations.buildForNameAndLatLng(null, null, null)

        then:
        criteria.count() == 0

        when:
        criteria = Organizations.buildForNameAndLatLng(org1.name, org1.location.lat, org1.location.lng)

        then:
        criteria.list() == [org1]
    }

    void "test criteria for active given admin staff ids"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        org1.status = OrgStatus.PENDING
        Organization org2 = TestUtils.buildOrg()
        org2.status = OrgStatus.APPROVED

        Staff s1 = TestUtils.buildStaff(org1)
        s1.status = StaffStatus.ADMIN
        Staff s2 = TestUtils.buildStaff(org2)
        s2.status = StaffStatus.STAFF
        Staff s3 = TestUtils.buildStaff(org2)
        s3.status = StaffStatus.ADMIN

        Organization.withSession { it.flush() }

        when:
        DetachedCriteria criteria = Organizations.buildActiveForAdminIds(null)

        then:
        criteria.count() == 0

        when:
        criteria = Organizations.buildActiveForAdminIds([s1.id])

        then: "staff is admin but org is not active"
        criteria.count() == 0

        when:
        criteria = Organizations.buildActiveForAdminIds([s2.id])

        then: "staff is not admin "
        criteria.count() == 0

        when:
        criteria = Organizations.buildActiveForAdminIds([s2, s3]*.id)

        then:
        criteria.list() == [org2]
    }
}
