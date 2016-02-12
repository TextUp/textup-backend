package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import org.textup.types.OrgStatus

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership])
@TestMixin(HibernateTestMixin)
@Unroll
class OrganizationSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        ResultFactory fac = getResultFactory()
        fac.messageSource = [getMessage:{ String c, Object[] p, Locale l -> c }] as MessageSource
    }
    private ResultFactory getResultFactory() {
        grailsApplication.mainContext.getBean("resultFactory")
    }

    void "test constraints"() {
        when:
        Organization org = new Organization()
        org.resultFactory = getResultFactory()
        int baseline = Organization.count()

        then:
        org.validate() == false

        when: "we fill in fields"
        String orgName = "OrgSpec"
        org.name = orgName
        org.location = new Location(address:"testing", lat:0G, lon:0G)

        then:
        org.validate() == true

        when: "we try to create an org with duplicate name-location"
        org.save(flush:true, failOnError:true)
        Organization org2 = new Organization(name:orgName)
        org2.resultFactory = getResultFactory()
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
        org2.save(flush:true, failOnError:true)

        then: "search for orgs to gives us both"
        Organization.count() == baseline + 2
        Organization.search(orgName).size() == 2
        Organization.search(orgName).size() == Organization.countSearch(orgName)
    }

    void "test operations on staff"() {
        given: "an organization"
        Organization org = new Organization(name:"OrgSpec2")
        org.resultFactory = getResultFactory()
        org.location = new Location(address:"testing", lat:0G, lon:0G)
        org.save(flush:true, failOnError:true)
        int baseline = Staff.count()

        when: "we add a staff"
        Result res = org.addStaff(username:"orgstaff1", password:"password",
            name:"Staff", email:"staff@textup.org", personalPhoneAsString:"1112223333")

        then:
        res.success == true
        res.payload instanceof Staff
        res.payload.validate() == true

        when: "we add an invalid staff"
        res.payload.save(flush:true, failOnError:true)
        res = org.addStaff(username:"orgstaff2", password:"password",
            name:"Staff")

        then:
        res.success == false
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1

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
        org.resultFactory = getResultFactory()
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
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1

        when: "we switch to a unique name"
        res = org.addTeam(name:"team 1", //team names CASE SENSITIVE!
            location:new Location(address:"testing", lat:0G, lon:0G))

        then:
        res.success == true
        res.payload instanceof Team
        res.payload.validate() == true
        assert res.payload.save(flush:true, failOnError:true)

        expect:
        org.teams.size() == 2
    }
}
