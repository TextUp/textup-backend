package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Shared
import spock.lang.Specification
import grails.plugin.springsecurity.SpringSecurityService
import org.textup.util.CustomSpec
import org.joda.time.DateTime
import static org.springframework.http.HttpStatus.*

@TestFor(TeamService)
@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class TeamServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
    }
    def cleanup() { 
        super.cleanupData()
    }

    void "test create"() {
    	when: "creation of a team with a nonexistent organization"
        Map createInfo = [:]
        Result res = service.create(createInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "teamService.create.orgNotFound"
        res.payload.status == NOT_FOUND

    	when: "we create a team with an invalid location"
        createInfo = [
            name:"Team 77",
            org:org.id, 
            location:[
                address:"testing address",
                lat:-888G,
                lon:-888G
            ]
        ]
        res = service.create(createInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 2

        when: "we create a team with an invalid team phone number"
        createInfo = [
            name:"Team 77",
            org:org.id, 
            location:[
                address:"testing address",
                lat:8G,
                lon:10G
            ],
            phone:"invalid123"
        ]
        res = service.create(createInfo)

        then:
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 1

    	when: "we create a team with a duplicate team phone number"
        createInfo = [
            name:"Team 77",
            org:org.id, 
            location:[
                address:"testing address",
                lat:8G,
                lon:10G
            ],
            phone:s1.phone.number.number
        ]
        res = service.create(createInfo)

    	then:
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 1

    	when: "we create a valid team"
        int baseline = Team.count(), 
            lBaseline = Location.count()
        String name = "Team 888"
        createInfo = [
            name:name,
            org:org.id, 
            location:[
                address:"testing address",
                lat:8G,
                lon:10G
            ]
        ]
        res = service.create(createInfo)
        assert res.success
        org.save(flush:true, failOnError:true)

    	then:
        Team.count() == baseline + 1
        Location.count() == lBaseline + 1
        res.payload instanceof Team 
        res.payload.name == name 
        res.payload.org.id == org.id
    }

    void "test update"() {
    	when: "we update a nonexistent team"
        Map updateInfo = [:]
        Result res = service.update(-88L, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "teamService.update.notFound"
        res.payload.status == NOT_FOUND

    	when: "we update a team with an invalid location"
        updateInfo = [location:[lat:-888G, lon:-888G]]
        res = service.update(t1.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 2

        when: "we update a team with an invalid phone number"
        updateInfo = [phone:"invalid123"]
        res = service.update(t1.id, updateInfo)

        then:
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 1

    	when: "we update a team with a duplicate team phone number"
        updateInfo = [phone:s1.phone.number.number]
        res = service.update(t1.id, updateInfo)

    	then:
        res.success == false 
        res.type == Constants.RESULT_VALIDATION
        res.payload.errorCount == 1

    	when: "we try to update with team actions that is not list"
        updateInfo = [doTeamActions:"I am not a list"]
        res = service.update(t1.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "teamService.update.teamActionNotList"
        res.payload.status == BAD_REQUEST

    	when: "we try to update team action with nonexistent staff member"
        updateInfo = [doTeamActions:[
            [id:-88L, action:Constants.TEAM_ACTION_ADD]
        ]]
        res = service.update(t1.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "teamService.update.staffNotFound"
        res.payload.status == NOT_FOUND

    	when: "we try to update team action with forbidden staff member"
        service.authService = [hasPermissionsForStaff: { Long sId -> false }]
        updateInfo = [doTeamActions:[
            [id:otherS3.id, action:Constants.TEAM_ACTION_ADD]
        ]]
        res = service.update(t1.id, updateInfo)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "teamService.update.staffForbidden"
        res.payload.status == FORBIDDEN

        when: "we update with team action with unspecified action"
        service.authService = [hasPermissionsForStaff: { Long sId -> true }]
        updateInfo = [doTeamActions:[
            [id:s1.id, action:"invalid"]
        ]]
        res = service.update(t1.id, updateInfo)

        then:
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "teamService.update.teamActionInvalid"
        res.payload.status == BAD_REQUEST

    	when: "we update a team with valid fields and team actions"
        int mBaseline = TeamMembership.count()
        String name = "Team12345", newPhoneNum = "3338838883"
        BigInteger newLat = 20G, newLon = -20G
        updateInfo = [
            name:name,
            phone:newPhoneNum,
            location:[
                lat:newLat,
                lon:newLon
            ],
            doTeamActions:[
                [id:s1.id, action:Constants.TEAM_ACTION_REMOVE],
                [id:s2.id, action:Constants.TEAM_ACTION_REMOVE],
                [id:s3.id, action:Constants.TEAM_ACTION_ADD]
            ]
        ]
        res = service.update(t1.id, updateInfo)
        assert res.success 
        t1.save(flush:true, failOnError:true)

    	then:
        TeamMembership.count() == mBaseline - 1 //removed two and added one
        res.payload instanceof Team 
        res.payload.id == t1.id 
        res.payload.name == name
        res.payload.phone.number.number == newPhoneNum
        res.payload.location.lat == newLat
        res.payload.location.lon == newLon
    }

    void "test delete"() {
    	when: "we delete a nonexistent team"
        Result res = service.delete(-88L)

    	then: 
        res.success == false 
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "teamService.delete.notFound"
        res.payload.status == NOT_FOUND

    	when: "we delete an existing team"
        int tBaseline = Team.count(), 
            mBaseline = TeamMembership.count(), 
            pBaseline = Phone.count(), 
            pNumBaseline = PhoneNumber.count(), 
            lBaseline = Location.count(), 
            tagBaseline = ContactTag.count(),
            cBaseline = Contact.count(), 
            rBaseline = Record.count(),
            iBaseline = RecordItem.count()
        res = service.delete(t1.id)
        assert res.success 
        //don't save with t1 or else you'll just re-save what you deleted
        org.save(flush:true, failOnError:true)

    	then:
        Team.count() == tBaseline - 1
        TeamMembership.count() == mBaseline - 2
        Phone.count() == pBaseline - 1
        PhoneNumber.count() == pNumBaseline - 2 //1 for team phone 1 for contact
        Location.count() == lBaseline - 1 
        ContactTag.count() == tagBaseline - 1
        Contact.count() == cBaseline - 1 
        Record.count() == rBaseline - 2 //1 for contact, 1 for tag
        RecordItem.count() == iBaseline - 2 //1 in contact, 1 in tag
    }
}
