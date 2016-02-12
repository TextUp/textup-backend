package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import org.textup.validator.PhoneNumber
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(TeamService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class TeamServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String _methodJustCalled

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.phoneService = [
            updatePhoneForNumber: { Phone p1, PhoneNumber pNum ->
                p1.number = pNum
                _methodJustCalled = "updatePhoneForNumber"
                new Result(type:ResultType.SUCCESS, success:true, payload:p1)
            },
            updatePhoneForApiId: { Phone p1, String apiId ->
                p1.numberAsString = "${iterationCount}123324901".take(10)
                _methodJustCalled = "updatePhoneForApiId"
                new Result(type:ResultType.SUCCESS, success:true, payload:p1)
            }
        ] as PhoneService
    }
    def cleanup() {
        super.cleanupData()
    }

    // // Create
    // // ------

    // void "test update team info"() {
    //     given: "baselines"
    //     int baseline = Team.count()
    //     int lBaseline = Location.count()
    //     int pBaseline = Phone.count()

    //     when: "we create a team with an invalid location"
    //     String name = "Team 77"
    //     String hex = "#909"
    //     String address = "testing address"
    //     Map createInfo = [
    //         name:name,
    //         org:org.id,
    //         hexColor:hex,
    //         location:[
    //             address:address,
    //             lat:-888G,
    //             lon:-888G
    //         ]
    //     ]
    //     Team team1 = new Team(org:org)
    //     Result res = service.updateTeamInfo(team1, createInfo)

    //     then:
    //     res.success == false
    //     res.type == ResultType.VALIDATION
    //     res.payload.errorCount == 2
    //     Team.count() == baseline
    //     Location.count() == lBaseline
    //     Phone.count() == pBaseline

    //     when: "we create a valid team"
    //     BigDecimal lat = 8G, lon = 10G
    //     createInfo.location.lat = lat
    //     createInfo.location.lon = lon
    //     res = service.updateTeamInfo(team1, createInfo)
    //     assert res.success
    //     org.save(flush:true, failOnError:true)

    //     then:
    //     res.payload instanceof Team
    //     res.payload.name == name
    //     res.payload.org.id == org.id
    //     res.payload.hexColor == hex
    //     res.payload.location.address == address
    //     res.payload.location.lat == lat
    //     res.payload.location.lon == lon
    //     Team.count() == baseline + 1
    //     Location.count() == lBaseline + 1
    //     Phone.count() == pBaseline

    //     when: "update away message when team has phone"
    //     tPh1.updateOwner(team1)
    //     tPh1.save(flush:true, failOnError:true)
    //     String msg = "you da best mon"
    //     res = service.updateTeamInfo(team1, [awayMessage:msg])

    //     then:
    //     res.payload instanceof Team
    //     res.payload.phone.awayMessage == msg
    //     Team.count() == baseline + 1
    //     Location.count() == lBaseline + 1
    //     Phone.count() == pBaseline
    // }

    // void "test create phone for team"() {
    //     given: "staff with no phone"
    //     Team team1 = new Team(name:"kiki's mane", org:org.id,
    //         location:new Location(address:"address", lat:8G, lon:10G).save())
    //     team1.save(flush:true, failOnError:true)
    //     int pBaseline = Phone.count()
    //     int oBaseline = PhoneOwnership.count()

    //     when: "try with neither phone nor phoneId"
    //     Result<Team> res = service.updateOrCreatePhone(team1, [:])

    //     then:
    //     res.success == true
    //     res.payload instanceof Team
    //     Phone.count() == pBaseline
    //     PhoneOwnership.count() == oBaseline

    //     when: "update with phone"
    //     String number = "${iterationCount}163388441".take(10)
    //     res = service.updateOrCreatePhone(team1, [phone:number])
    //     assert res.success
    //     team1.save(flush:true, failOnError:true)

    //     then:
    //     Phone.count() == pBaseline + 1
    //     PhoneOwnership.count() == oBaseline + 1
    //     res.payload instanceof Team
    //     res.payload.id == team1.id
    //     res.payload.phone != null
    // }

    // void "test create"() {
    //     given: "baselines"
    //     int baseline = Team.count()
    //     int lBaseline = Location.count()

    // 	when: "creation of a team with a nonexistent organization"
    //     Map createInfo = [:]
    //     Result res = service.create(createInfo)

    // 	then:
    //     res.success == false
    //     res.type == ResultType.MESSAGE_STATUS
    //     res.payload.code == "teamService.create.orgNotFound"
    //     res.payload.status == NOT_FOUND

    // 	when: "we create a valid team"
    //     String name = "Team 888"
    //     createInfo = [
    //         name:name,
    //         org:org.id,
    //         location:[
    //             address:"testing address",
    //             lat:8G,
    //             lon:10G
    //         ]
    //     ]
    //     res = service.create(createInfo)
    //     assert res.success
    //     org.save(flush:true, failOnError:true)

    // 	then:
    //     res.payload instanceof Team
    //     res.payload.name == name
    //     res.payload.org.id == org.id
    //     Team.count() == baseline + 1
    //     Location.count() == lBaseline + 1
    // }

    // Update
    // ------

    void "test find team from id"() {
        when: "nonexistent id"
        Result<Team> res = service.findTeamFromId(-88L)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "teamService.update.notFound"

        when: "valid id"
        res = service.findTeamFromId(t1.id)

        then:
        res.success == true
        res.payload instanceof Team
        res.payload == t1
    }

    void "test update phone number with phone number"() {
        given:
        int baseline = Phone.count()

        when: "with phone number"
        _methodJustCalled = null
        Result<Team> res = service.updateOrCreatePhone(t1, [phone:"1112223333"])

        then:
        res.success == true
        res.payload instanceof Team
        _methodJustCalled == "updatePhoneForNumber"
        Phone.count() == baseline

        when: "with api id"
        _methodJustCalled = null
        res = service.updateOrCreatePhone(t1, [phoneId:"hello!"])

        then:
        res.success == true
        res.payload instanceof Team
        _methodJustCalled == "updatePhoneForApiId"
        Phone.count() == baseline
    }

    void "test team actions edge cases"() {
        when: "no team actions"
        Result<Team> res = service.handleTeamActions(t1, [:])

        then:
        res.success == true
        res.payload instanceof Team
        res.payload.id == t1.id

        when: "we try to update with team actions that is not list"
        Map updateInfo = [doTeamActions:"I am not a list"]
        res = service.handleTeamActions(t1, updateInfo)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "teamService.update.teamActionNotList"
        res.payload.status == BAD_REQUEST

        when: "we try to update team action with nonexistent staff member"
        updateInfo = [doTeamActions:[
            [id:-88L, action:Constants.TEAM_ACTION_ADD]
        ]]
        res = service.handleTeamActions(t1, updateInfo)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "teamService.update.staffNotFound"
        res.payload.status == NOT_FOUND

        when: "we try to update team action with forbidden staff member"
        service.authService = [hasPermissionsForStaff: { Long sId ->
            false
        }] as AuthService
        updateInfo = [doTeamActions:[
            [id:otherS3.id, action:Constants.TEAM_ACTION_ADD]
        ]]
        res = service.handleTeamActions(t1, updateInfo)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "teamService.update.staffForbidden"
        res.payload.status == FORBIDDEN

        when: "we update with team action with unspecified action"
        service.authService = [hasPermissionsForStaff: { Long sId ->
            true
        }] as AuthService
        updateInfo = [doTeamActions:[
            [id:s1.id, action:"invalid"]
        ]]
        res = service.handleTeamActions(t1, updateInfo)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "teamService.update.teamActionInvalid"
        res.payload.status == BAD_REQUEST
    }

    void "test team actions valid"() {
        when: "we update a team with valid fields and team actions"
        service.authService = [hasPermissionsForStaff: { Long sId ->
            true
        }] as AuthService
        Map updateInfo = [
            doTeamActions:[
                [id:s1.id, action:Constants.TEAM_ACTION_REMOVE],
                [id:s2.id, action:Constants.TEAM_ACTION_REMOVE],
                [id:s3.id, action:Constants.TEAM_ACTION_ADD]
            ]
        ]
        Result<Team> res = service.handleTeamActions(t1, updateInfo)
        assert res.success
        t1.save(flush:true, failOnError:true)

        then:
        res.payload instanceof Team
        res.payload.id == t1.id
        res.payload.members.contains(s1) == false
        res.payload.members.contains(s2) == false
        res.payload.members.contains(s3) == true
    }

    void "test update"() {
        given: "baselines"
        int baseline = Team.count()
        int lBaseline = Location.count()
        int pBaseline = Phone.count()

    	when: "we update a team with an invalid location"
        Map updateInfo = [location:[lat:-888G, lon:-888G]]
        Result res = service.update(t1.id, updateInfo)

    	then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 2
        Team.count() == baseline
        Location.count() == lBaseline
        Phone.count() == pBaseline

        when: "we update with valid location"
        BigDecimal lat = 8G, lon = 10G
        updateInfo.location.lat = lat
        updateInfo.location.lon = lon
        res = service.update(t1.id, updateInfo)

        then:
        res.success == true
        res.payload instanceof Team
        res.payload.location.lat == lat
        res.payload.location.lon == lon
        Team.count() == baseline
        Location.count() == lBaseline
        Phone.count() == pBaseline
    }

    // Delete
    // ------

    void "test delete"() {
    	when: "we delete a nonexistent team"
        Result res = service.delete(-88L)

    	then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.code == "teamService.delete.notFound"
        res.payload.status == NOT_FOUND

    	when: "we delete an existing team"
        res = service.delete(t1.id)
        assert res.success
        org.save(flush:true, failOnError:true)

    	then:
        t1.isDeleted == true
        t1.org.teams.contains(t1) == false
        t1.members.every { !it.teams.contains(t1) }
    }
}
