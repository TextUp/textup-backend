package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.PhoneNumber
import spock.lang.Shared
import spock.lang.Specification

@TestFor(TeamService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class TeamServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        super.setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
        service.phoneService = [
            mergePhone: { Team t1, Map body, String timezone ->
                new Result(success:ResultStatus.OK, payload:t1)
            }
        ] as PhoneService
    }
    def cleanup() {
        super.cleanupData()
    }

    // Create
    // ------

    void "test update team info"() {
        given: "baselines"
        int baseline = Team.count()
        int lBaseline = Location.count()
        int pBaseline = Phone.count()

        when: "we create a team with an invalid location"
        String name = "Team 77"
        String hex = "#909"
        String address = "testing address"
        Map createInfo = [
            name:name,
            org:org.id,
            hexColor:hex,
            location:[
                address:address,
                lat:-888G,
                lon:-888G
            ]
        ]
        Team team1 = new Team(org:org)
        Result res = service.updateTeamInfo(team1, createInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 2
        Team.count() == baseline
        Location.count() == lBaseline
        Phone.count() == pBaseline

        when: "we create a valid team"
        BigDecimal lat = 8G, lon = 10G
        createInfo.location.lat = lat
        createInfo.location.lon = lon
        res = service.updateTeamInfo(team1, createInfo)
        assert res.success
        res.payload.save(flush:true, failOnError:true)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof Team
        res.payload.name == name
        res.payload.org.id == org.id
        res.payload.hexColor == hex
        res.payload.location.address == address
        res.payload.location.lat == lat
        res.payload.location.lon == lon
        Team.count() == baseline + 1
        Location.count() == lBaseline + 1
        Phone.count() == pBaseline

        when: "update away message when team has phone"
        tPh1.updateOwner(team1)
        tPh1.merge(flush:true, failOnError:true)
        String msg = "you da best mon",
            originalAwayMsg = team1.phone.awayMessage
        res = service.updateTeamInfo(team1, [awayMessage:msg])

        then: "no change because update happens in phoneService"
        res.status == ResultStatus.OK
        res.payload instanceof Team
        res.payload.phone.awayMessage != msg
        res.payload.phone.awayMessage == originalAwayMsg
        Team.count() == baseline + 1
        Location.count() == lBaseline + 1
        Phone.count() == pBaseline
    }

    void "test create"() {
        given: "baselines"
        int baseline = Team.count()
        int lBaseline = Location.count()

    	when: "creation of a team with a nonexistent organization"
        Map createInfo = [:]
        Result res = service.create(createInfo, null)

    	then:
        res.success == false
        res.errorMessages[0] == "teamService.create.orgNotFound"
        res.status == ResultStatus.NOT_FOUND

    	when: "we create a valid team"
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
        res = service.create(createInfo, null)
        assert res.success
        res.payload.save(flush:true, failOnError:true)

    	then:
        res.status == ResultStatus.CREATED
        res.payload instanceof Team
        res.payload.name == name
        res.payload.org.id == org.id
        Team.count() == baseline + 1
        Location.count() == lBaseline + 1
    }

    // Update
    // ------

    void "test find team from id"() {
        when: "nonexistent id"
        Result<Team> res = service.findTeamFromId(-88L)

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "teamService.update.notFound"

        when: "valid id"
        res = service.findTeamFromId(t1.id)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Team
        res.payload.id == t1.id
    }

    void "test team actions edge cases"() {
        when: "no team actions"
        Result<Team> res = service.handleTeamActions(t1, [:])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Team
        res.payload.id == t1.id

        when: "we try to update with team actions that is not list"
        Map updateInfo = [doTeamActions:"I am not a list"]
        res = service.handleTeamActions(t1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages.any{ it.contains("emptyOrNotACollection") }

        when: "we try to update team action with nonexistent staff member"
        updateInfo = [doTeamActions:[
            [id:-88L, action:Constants.TEAM_ACTION_ADD]
        ]]
        res = service.handleTeamActions(t1, updateInfo)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages.contains("actionContainer.invalidActions")

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
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages.contains("actionContainer.invalidActions")

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
        res.errorMessages[0] == "teamService.update.staffForbidden"
        res.status == ResultStatus.FORBIDDEN
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
        res.status == ResultStatus.OK
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
        Result res = service.update(t1.id, updateInfo, null)

    	then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 2
        Team.count() == baseline
        Location.count() == lBaseline
        Phone.count() == pBaseline

        when: "we update with valid location"
        BigDecimal lat = 8G, lon = 10G
        updateInfo.location.lat = lat
        updateInfo.location.lon = lon
        res = service.update(t1.id, updateInfo, null)

        then:
        res.success == true
        res.status == ResultStatus.OK
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
        res.errorMessages[0] == "teamService.delete.notFound"
        res.status == ResultStatus.NOT_FOUND

    	when: "we delete an existing team"
        res = service.delete(t1.id)
        assert res.success
        // HYPOTHESIS: transction is committed and session closes after the
        // service method returns. Therefore, we need to re-fetch the team
        // in order to get the updated properties
        t1 = Team.get(t1.id)

    	then:
        res.status == ResultStatus.NO_CONTENT
        t1.isDeleted == true
        t1.org.teams.contains(t1) == false
        t1.members.every { !it.teams.contains(t1) }
    }
}
