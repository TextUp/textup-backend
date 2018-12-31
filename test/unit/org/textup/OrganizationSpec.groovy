package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import java.util.UUID
import org.springframework.context.MessageSource
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
@Unroll
class OrganizationSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "test constraints"() {
        when:
        Organization org = new Organization()
        int baseline = Organization.count()

        then:
        org.validate() == false
        org.timeout == Constants.DEFAULT_LOCK_TIMEOUT_MILLIS

        when: "we fill in fields"
        String orgName = UUID.randomUUID().toString()
        org.name = orgName
        org.location = new Location(address:"testing", lat:0G, lon:0G)

        then:
        org.validate() == true

        when: "we have an out-of-bounds timeout"
        org.timeout = Constants.MAX_LOCK_TIMEOUT_MILLIS * 2

        then:
        org.validate() == false
        org.errors.getFieldErrorCount("timeout") == 1

        when: "we have a missing or zero timeout"
        org.timeout = 0

        then:
        org.validate() == false
        org.errors.getFieldErrorCount("timeout") == 1

        when: "we try to create an org with duplicate name-location"
        org.timeout = Constants.DEFAULT_LOCK_TIMEOUT_MILLIS
        assert org.save(flush:true, failOnError:true)

        Organization org2 = new Organization(name:orgName)
        org2.location = new Location(address:"testing", lat:0G, lon:0G)

        then:
        org2.validate() == false
        org2.errors.errorCount == 1

        when: "we switch to a unique location but keep duplicate name"
        org2.location.lon = 8G

        then:
        org2.validate() == true

        when: "we approve both organizations"
        org.status = OrgStatus.APPROVED
        org2.status = OrgStatus.APPROVED
        [org, org2]*.save(flush:true, failOnError:true)

        then: "search for orgs to gives us both"
        Organization.count() == baseline + 2
        Organization.search(orgName).size() == 2
        Organization.search(orgName).size() == Organization.countSearch(orgName)
    }

    void "test away message suffix constraints"() {
        given: "an org"
        Organization org = new Organization(name:"OrgSpec2")
        org.location = TestUtils.buildLocation()
        assert org.validate()

        when: "away message suffix is null"
        org.awayMessageSuffix = null

        then:
        org.validate()

        when: "away message suffix is absent"
        org.awayMessageSuffix = ""

        then:
        org.validate()

        when: "away message suffix is too long"
        org.awayMessageSuffix = TestUtils.buildVeryLongString()

        then:
        org.validate() == false
        org.errors.getFieldErrorCount("awayMessageSuffix")

        when: "away message suffix is present and within length constraints"
        org.awayMessageSuffix = TestUtils.randString()

        then:
        org.validate()
    }

    void "test operations on staff"() {
        given: "an organization"
        Organization org = new Organization(name:"OrgSpec2")
        org.location = new Location(address:"testing", lat:0G, lon:0G)
        org.save(flush:true, failOnError:true)
        int baseline = Staff.count()

        when: "we add a staff"
        Result res = org.addStaff(username:"orgstaff1", password:"password",
            name:"Staff", email:"staff@textup.org", personalPhoneAsString:"1112223333",
            lockCode:Constants.DEFAULT_LOCK_CODE)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Staff
        res.payload.validate() == true

        when: "we add an invalid staff"
        res.payload.save(flush:true, failOnError:true)
        res = org.addStaff(username:"orgstaff2", password:"password",
            name:"Staff", lockCode:Constants.DEFAULT_LOCK_CODE)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        expect: "getting staff by status"
        Staff.count() == baseline + 1
        org.people.size() == 1
        org.pending.size() == 1
        org.admins.size() == 0
        org.staff.size() == 0
        org.blocked.size() == 0
    }

    void "test operations on teams"() {
        given: "an organization"
        Organization org = new Organization(name:"OrgSpec3")
        org.location = new Location(address:"testing", lat:0G, lon:0G)
        org.save(flush:true, failOnError:true)

        when: "we add a valid team"
        Result res = org.addTeam(name:"Team 1",
            location:new Location(address:"testing", lat:0G, lon:0G))

        then:
        res.success == true
        res.payload instanceof Team
        res.payload.validate() == true

        when: "we try to add a duplicate team"
        res.payload.save(flush:true, failOnError:true)
        res = org.addTeam(name:"Team 1",
            location:new Location(address:"testing", lat:0G, lon:0G))

        then:
        org.teams.size() == 1
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "we switch to a unique name"
        res = org.addTeam(name:"team 1", //team names CASE SENSITIVE!
            location:new Location(address:"testing", lat:0G, lon:0G))

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Team
        res.payload.validate() == true
        assert res.payload.save(flush:true, failOnError:true)

        expect:
        org.teams.size() == 2
    }
}
