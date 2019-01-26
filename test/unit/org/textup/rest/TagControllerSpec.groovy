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
@TestFor(TagController)
@TestMixin(HibernateTestMixin)
class TagControllerSpec extends CustomSpec {

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

    // protected void mockForList() {
    //     controller.authService = [
    //         getLoggedInAndActive:{ Staff.findByUsername(loggedInUsername) },
    //         hasPermissionsForTeam:{ Long id -> true }
    //     ] as AuthService
    // }

    // void "test list with no ids"() {
    //     when:
    //     mockForList()
    //     request.method = "GET"
    //     controller.index()
    //     Staff loggedIn = Staff.findByUsername(loggedInUsername)
    //     List<Long> ids = TypeConversionUtils.allTo(Long, loggedIn.phone.tags*.id)

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.size() == ids.size()
    //     response.json*.id.every { ids.contains(it as Long) }
    // }

    // void "test list with team id"() {
    //     when:
    //     mockForList()
    //     params.teamId = t1.id
    //     request.method = "GET"
    //     controller.index()
    //     List<Long> ids = TypeConversionUtils.allTo(Long, t1.phone.tags*.id)

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.size() == ids.size()
    //     response.json*.id.every { ids.contains(it as Long) }
    // }

    // // Show
    // // ----

    // void "test show nonexistent tag"() {
    //     when:
    //     request.method = "GET"
    //     params.id = -88L
    //     controller.show()

    //     then:
    //     response.status == HttpServletResponse.SC_NOT_FOUND
    // }

    // void "test show forbidden tag"() {
    //     given:
    //     controller.authService = [
    //         hasPermissionsForTag:{ Long id -> false },
    //     ] as AuthService

    //     when:
    //     request.method = "GET"
    //     params.id = tag1.id
    //     controller.show()

    //     then:
    //     response.status == HttpServletResponse.SC_FORBIDDEN
    // }

    // void "test show tag"() {
    //     given:
    //     controller.authService = [
    //         hasPermissionsForTag:{ Long id -> true },
    //     ] as AuthService

    //     when:
    //     request.method = "GET"
    //     params.id = tag1.id
    //     controller.show()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.id == tag1.id
    // }

    // // Save
    // // ----

    // protected void mockForSave() {
    //     controller.tagService = [createForStaff:{ Map body ->
    //         new Result(payload:tag1, status:ResultStatus.CREATED)
    //     }, createForTeam:{ Long tId, Map body ->
    //         new Result(payload:teTag1, status:ResultStatus.CREATED)
    //     }] as TagService
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         getIsActive:{ true },
    //         hasPermissionsForTeam:{ Long id -> true }
    //     ] as AuthService
    // }

    // void "test save with no id"() {
    //     when:
    //     mockForSave()
    //     request.json = "{'tag':{}}"
    //     request.method = "POST"
    //     controller.save()

    //     then:
    //     response.status == HttpServletResponse.SC_CREATED
    //     response.json.id == tag1.id
    // }

    // void "test save with team id"() {
    //     when:
    //     mockForSave()
    //     request.json = "{'tag':{}}"
    //     params.teamId = t1.id
    //     request.method = "POST"
    //     controller.save()

    //     then:
    //     response.status == HttpServletResponse.SC_CREATED
    //     response.json.id == teTag1.id
    // }

    // // Update
    // // ------

    // void "test update nonexistent tag"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> false }
    //     ] as AuthService

    //     when:
    //     request.json = "{'tag':{}}"
    //     params.id = -88L
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_NOT_FOUND
    // }

    // void "test update forbidden tag"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         hasPermissionsForTag:{ Long id -> false }
    //     ] as AuthService

    //     when:
    //     request.json = "{'tag':{}}"
    //     params.id = tag1.id
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_FORBIDDEN
    // }

    // void "test update tag"() {
    //     given:
    //     controller.tagService = [update:{ Long cId, Map body ->
    //         new Result(payload:tag1, status:ResultStatus.OK)
    //     }] as TagService
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         hasPermissionsForTag:{ Long id -> true }
    //     ] as AuthService

    //     when:
    //     request.json = "{'tag':{}}"
    //     params.id = tag1.id
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.id == tag1.id
    // }

    // // Delete
    // // ------

    // void "test delete nonexistent tag"() {
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

    // void "test delete forbidden tag"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         hasPermissionsForTag:{ Long id -> false }
    //     ] as AuthService

    //     when:
    //     params.id = tag1.id
    //     request.method = "DELETE"
    //     controller.delete()

    //     then:
    //     response.status == HttpServletResponse.SC_FORBIDDEN
    // }

    // void "test delete tag"() {
    //     given:
    //     controller.tagService = [delete:{ Long cId ->
    //         new Result(payload:null, status:ResultStatus.NO_CONTENT)
    //     }] as TagService
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         hasPermissionsForTag:{ Long id -> true }
    //     ] as AuthService

    //     when:
    //     params.id = tag1.id
    //     request.method = "DELETE"
    //     controller.delete()

    //     then:
    //     response.status == HttpServletResponse.SC_NO_CONTENT
    // }
}
