package org.textup.rest

import grails.plugin.jodatime.converters.JodaConverters
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
@TestFor(OrganizationController)
@TestMixin(HibernateTestMixin)
class OrganizationControllerSpec extends CustomSpec {

    // static doWithSpring = {
    //     resultFactory(ResultFactory)
    // }

    // def setup() {
    //     setupData()
    //     JodaConverters.registerJsonAndXmlMarshallers()

    //     controller.authService = [getIsActive:{ -> true }] as AuthService
    // }

    // def cleanup() {
    //     cleanupData()
    // }

    // // List
    // // ----

    // void "test list"() {
    //     when:
    //     request.method = "GET"
    //     controller.index()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.size() == Organization.count()
    // }

    // void "test list with successful search"() {
    //     when:
    //     request.method = "GET"
    //     params.search = "organ"
    //     controller.index()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.organizations.size() ==
    //         Organization.countSearch(StringUtils.toQuery(params.search))
    // }

    // void "test list with unsuccessful search"() {
    //     when:
    //     request.method = "GET"
    //     params.search = "sdfjksljsdf"
    //     controller.index()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.organizations != null //when empty, we manually add in the root
    //     response.json.organizations?.size() == 0
    // }

    // void "test list for invalid status"() {
    //     when:
    //     request.method = "GET"
    //     params["status[]"] = ["invalid"]
    //     controller.index()

    //     then: "returns empty set"
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.organizations.size() == 0
    // }

    // void "test list for valid statuses"() {
    //     when:
    //     request.method = "GET"
    //     params["status[]"] = [OrgStatus.APPROVED.toString()]
    //     controller.index()

    //     then: "returns empty set"
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.organizations.size() ==
    //         Organization.countByStatusInList([OrgStatus.APPROVED])
    // }

    // // Show
    // // ----

    // void "test show nonexistent"() {
    //     when:
    //     request.method = "GET"
    //     params.id = -88L
    //     controller.show()

    //     then:
    //     response.status == HttpServletResponse.SC_NOT_FOUND
    // }

    // void "test show"() {
    //     when:
    //     request.method = "GET"
    //     params.id = org.id
    //     controller.show()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.id == org.id
    // }

    // // Save
    // // ----

    // void "test save"() {
    //     when:
    //     request.json = "{'organization':{}}"
    //     request.method = "POST"
    //     controller.save()

    //     then:
    //     response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
    // }

    // // Update
    // // ------

    // void "test update a nonexistent org"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> false }
    //     ] as AuthService

    //     when:
    //     request.json = "{'organization':{}}"
    //     params.id = -88L
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_NOT_FOUND
    // }

    // void "test update a forbidden org"() {
    //     given:
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         isAdminAt:{ Long id -> false }
    //     ] as AuthService

    //     when:
    //     request.json = "{'organization':{}}"
    //     params.id = c1.id
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_FORBIDDEN
    // }

    // void "test update"() {
    //     given:
    //     controller.organizationService = [update:{ Long cId, Map body ->
    //         new Result(payload:org, status:ResultStatus.OK)
    //     }] as OrganizationService
    //     controller.authService = [
    //         exists:{ Class clazz, Long id -> true },
    //         isAdminAt:{ Long id -> true }
    //     ] as AuthService

    //     when:
    //     request.json = "{'organization':{}}"
    //     params.id = org.id
    //     request.method = "PUT"
    //     controller.update()

    //     then:
    //     response.status == HttpServletResponse.SC_OK
    //     response.json.id == org.id
    // }

    // // Delete
    // // ------

    // void "test delete"() {
    //     when:
    //     params.id = org.id
    //     request.method = "DELETE"
    //     controller.delete()

    //     then:
    //     response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
    // }
}
