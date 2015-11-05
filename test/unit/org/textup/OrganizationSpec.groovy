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

@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
@Unroll
class OrganizationSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        ResultFactory fac = getResultFactory()
        fac.messageSource = [getMessage:{ String c, Object[] p, Locale l -> c }] as MessageSource
        Organization.metaClass.constructor = { ->
            def instance = grailsApplication.mainContext.getBean(Organization.name)
            instance.resultFactory = getResultFactory()
            instance
        }
        Organization.metaClass.constructor = { Map m->
            def instance = new Organization() 
            instance.properties = m
            instance.resultFactory = getResultFactory()
            instance
        }
    }
    private ResultFactory getResultFactory() {
        grailsApplication.mainContext.getBean("resultFactory")
    }

    ////////////////////////////////
    // Deletion not yet supported //
    ////////////////////////////////

    void "test constraints"() {
        when: 
        Organization org = new Organization()

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
        org2.location = new Location(address:"testing", lat:0G, lon:0G)

        then: 
        org2.validate() == false 
        org2.errors.errorCount == 1

        when: "we switch to a unique location but keep duplicate name"
        org2.location.lon = 8G

        then: 
        org2.validate() == true 
    }

    void "test operations on staff"() {
        given: "an organization"
        Organization org = new Organization(name:"OrgSpec2")
        org.location = new Location(address:"testing", lat:0G, lon:0G)
        org.save(flush:true, failOnError:true)

        when: "we add a staff"
        Result res = org.addStaff(username:"orgstaff1", password:"password", 
            name:"Staff", email:"staff@textup.org", personalPhoneNumberAsString:"1112223333")

        then: 
        res.success == true 
        res.payload instanceof Staff 
        res.payload.validate() == true 

        when: "we add an invalid staff"
        res.payload.save(flush:true, failOnError:true)
        res = org.addStaff(username:"orgstaff2", password:"password", 
            name:"Staff", email:"staff@textup.org")

        then: 
        res.success == false 
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1
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
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1

        when: "we switch to a unique name"
        res = org.addTeam(name:"team 1",
            location:new Location(address:"testing", lat:0G, lon:0G)) //team names CASE SENSITIVE!

        then: 
        res.success == true 
        res.payload instanceof Team
        res.payload.validate() == true 

        when: "we try to delete a null team"
        Team validTeam = res.payload
        validTeam.save(flush:true, failOnError:true)
        assert org.teams.size() == 2
        res = org.deleteTeam()

        then: 
        res.success == false 
        res.payload instanceof Map
        res.payload.code == "organization.error.teamNotFound"

        when: "we delete a team"
        int baseline = Team.count()
        res = org.deleteTeam(validTeam)
        assert res.success
        org.save(flush:true, failOnError:true)

        then:
    	org.teams.size() == 1
        Team.count() == baseline - 1
    }
}
