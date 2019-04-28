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
@TestFor(OrganizationService)
@TestMixin(HibernateTestMixin)
class OrganizationServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        Location loc1 = TestUtils.buildLocation()
        TypeMap locMap = TestUtils.randTypeMap()
        TypeMap body = TypeMap.create(location: locMap, name: TestUtils.randString())

        int oBaseline = Organization.count()

        service.locationService = GroovyMock(LocationService)

        when:
        Result res = service.tryFindOrCreate(body)

        then:
        1 * service.locationService.tryCreate(locMap) >> Result.createSuccess(loc1)
        res.status == ResultStatus.CREATED
        res.payload.name == body.name
        res.payload.location == loc1
        Organization.count() == oBaseline + 1

        when:
        body.id = res.payload.id
        res = service.tryFindOrCreate(body)

        then:
        0 * service.locationService._
        res.status == ResultStatus.OK
        res.payload.id == body.id
    }

    void "test update"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        TypeMap body1 = TypeMap.create(name: TestUtils.randString())
        TypeMap locMap = TypeMap.create(address: TestUtils.randString())
        TypeMap body2 = TypeMap.create(name: TestUtils.randString(),
            timeout: Constants.DEFAULT_LOCK_TIMEOUT_MILLIS + 1,
            awayMessageSuffix: TestUtils.randString(),
            location: locMap)

        service.locationService = GroovyMock(LocationService)

    	when: "we try to update a nonexistent organization"
        Result res = service.tryUpdate(null, null)

    	then:
        res.status == ResultStatus.NOT_FOUND

    	when: "we update location with invalid fields"
        res = service.tryUpdate(org1.id, body1)

    	then:
        1 * service.locationService.tryUpdate(org1.location, TypeMap.create()) >> Result.void()
        res.status == ResultStatus.OK
        res.payload.name == body1.name

    	when: "we update with valid fields"
        res = service.tryUpdate(org1.id, body2)

    	then:
        1 * service.locationService.tryUpdate(org1.location, locMap) >> Result.void()
    	res.status == ResultStatus.OK
        res.payload instanceof Organization
        res.payload.name == body2.name
        res.payload.timeout == body2.timeout
        res.payload.awayMessageSuffix == body2.awayMessageSuffix
    }
}
