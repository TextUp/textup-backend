package org.textup.util.domain

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
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
class RolesSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test trying to find user role"() {
        given:
        int rBaseline = Role.count()

        when:
        Result res = Roles.tryGetUserRole()

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof Role
        Role.count() == rBaseline + 1

        when:
        Role role1 = res.payload
        res = Roles.tryGetUserRole()

        then:
        res.status == ResultStatus.OK
        res.payload == role1
        Role.count() == rBaseline + 1
    }

    void "test trying to find admin role"() {
        given:
        int rBaseline = Role.count()

        when:
        Result res = Roles.tryGetAdminRole()

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof Role
        Role.count() == rBaseline + 1

        when:
        Role role1 = res.payload
        res = Roles.tryGetAdminRole()

        then:
        res.status == ResultStatus.OK
        res.payload == role1
        Role.count() == rBaseline + 1
    }
}
