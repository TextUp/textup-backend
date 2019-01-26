package org.textup.rest

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

// TODO

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(TeamController)
@TestMixin(HibernateTestMixin)
class TeamControllerSpec extends CustomSpec {

    // static doWithSpring = {
    //     resultFactory(ResultFactory)
    // }
    // def setup() {
    //     super.setupData()
    // }
    // def cleanup() {
    //     super.cleanupData()
    // }

    // // List
    // // ----

    // protected mockForList() {
    //     controller.authService = [
    //         getLoggedInAndActive:{ Staff.findByUsername(loggedInUsername) },
    //         isAdminAt:{ Long id -> true }
    //     ] as AuthService
    // }

    // void "test list with staff id"() {
    //     when:
    //     mockForList()
    //     request.method = "GET"
    //     controller.index()
    //     Staff loggedIn = Staff.findByUsername(loggedInUsername)
    //     List<Long> ids = TypeConversionUtils.allTo(Long, loggedIn.teams*.id)

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.size() == ids.size()
    //     response.json*.id.every { ids.contains(it as Long) }
    // }

    // void "test list with nonexistent org id"() {
    //     when:
    //     mockForList()
    //     request.method = "GET"
    //     params.organizationId = -88L
    //     controller.index()

    //     then:
    //     response.status == HttpServletResponse.SC_NOT_FOUND
    // }

    // void "test list with org id"() {
    //     when:
    //     mockForList()
    //     request.method = "GET"
    //     params.organizationId = org.id
    //     controller.index()
    //     List<Long> ids = TypeConversionUtils.allTo(Long, org.teams*.id)

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.size() == ids.size()
    //     response.json*.id.every { ids.contains(it as Long) }
    // }

    // // Show
    // // ----

    // void "test show nonexistent team"() {
    //     when:
    //     request.method = "GET"
    //     params.id = -88L
    //     controller.show()

    //     then:
    //     response.status == HttpServletResponse.SC_NOT_FOUND
    // }

    // void "test show forbidden team"() {
    //     given:
    //     controller.authService = [
    //         hasPermissionsForTeam:{ Long id -> false }
    //     ] as AuthService

    //     when:
    //     request.method = "GET"
    //     params.id = t1.id
    //     controller.show()

    //     then:
    //     response.status == HttpServletResponse.SC_FORBIDDEN
    // }

    // void "test show team"() {
    //     given:
    //     controller.authService = [
    //         hasPermissionsForTeam:{ Long id -> true },
    //     ] as AuthService

    //     when:
    //     request.method = "GET"
    //     params.id = t1.id
    //     controller.show()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.id == t1.id
    // }

    // // Save
    // // ----

    // void "test save team"() {
    //     given:
    //     controller.teamService = [create:{ Map body, String timezone ->
    //         new Result(payload:t1, status:ResultStatus.CREATED)
    //     }] as TeamService

    //     when:
    //     request.json = "{'team':{}}"
    //     params.id = t1.id
    //     request.method = "POST"
    //     controller.save()

    //     then:
    //     response.status == HttpServletResponse.SC_CREATED
    //     response.json.id == t1.id

    // }

    // // Update
    // // ------

    // void "test update nonexistent team"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> false }
    //     ] as AuthService

    //     when:
    //     request.json = "{'team':{}}"
    //     params.id = -88L
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_NOT_FOUND
    // }

    // void "test update forbidden team"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         hasPermissionsForTeam:{ Long id -> false }
    //     ] as AuthService

    //     when:
    //     request.json = "{'team':{}}"
    //     params.id = t1.id
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_FORBIDDEN
    // }

    // void "test update team"() {
    //     given:
    //     controller.teamService = [update:{ Long cId, Map body, String timezone ->
    //         new Result(payload:t1, status:ResultStatus.OK)
    //     }] as TeamService
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         hasPermissionsForTeam:{ Long id -> true }
    //     ] as AuthService

    //     when:
    //     request.json = "{'team':{}}"
    //     params.id = t1.id
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.id == t1.id
    // }

    // // Delete
    // // ------

    // void "test delete nonexistent team"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> false }
    //     ] as AuthService

    //     when:
    //     params.id = -88L
    //     request.method = "DELETE"
    //     controller.delete()

    //     then:
    //     response.status == HttpServletResponse.SC_NOT_FOUND
    // }

    // void "test delete forbidden team"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         isAdminForTeam:{ Long id -> false }
    //     ] as AuthService

    //     when:
    //     params.id = t1.id
    //     request.method = "DELETE"
    //     controller.delete()

    //     then:
    //     response.status == HttpServletResponse.SC_FORBIDDEN
    // }

    // void "test delete team"() {
    //     given:
    //     controller.teamService = [delete:{ Long tId ->
    //         new Result(payload:null, status:ResultStatus.NO_CONTENT)
    //     }] as TeamService
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         isAdminForTeam:{ Long id -> true }
    //     ] as AuthService

    //     when:
    //     params.id = t1.id
    //     request.method = "DELETE"
    //     controller.delete()

    //     then:
    //     response.status == HttpServletResponse.SC_NO_CONTENT
    // }
}
