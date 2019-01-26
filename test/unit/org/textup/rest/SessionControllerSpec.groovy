package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
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
@TestFor(SessionController)
@TestMixin(HibernateTestMixin)
class SessionControllerSpec extends CustomSpec {

  //   static doWithSpring = {
  //       resultFactory(ResultFactory)
  //   }
  //   def setup() {
  //       setupData()
  //   }
  //   def cleanup() {
  //       cleanupData()
  //   }

  //   // List
  //   // ----

  //   protected void mockForList() {
		// controller.authService = [
		// 	hasPermissionsForTeam:{ Long tId -> true },
		// 	getLoggedInAndActive: { -> s1 }
		// ] as AuthService
  //   }

  //   void "test list with no id"() {
  //   	given:
  //   	mockForList()
  //   	IncomingSession sess1 = new IncomingSession(phone:p1,
  //   		numberAsString:"1112223333")
  //   	sess1.save(flush:true, failOnError:true)

  //   	when:
  //   	request.method = "GET"
  //   	controller.index()
  //   	Staff loggedIn = Staff.findByUsername(loggedInUsername)
  //       List<Long> ids = TypeConversionUtils.allTo(Long, loggedIn.phone.sessions*.id)

  //   	then: "implicit staff"
  //       response.status == HttpServletResponse.SC_OK
  //       response.json.size() == ids.size()
  //       response.json*.id.every { ids.contains(it as Long) }
  //   }

  //   void "test with team id"() {
  //   	given:
  //   	mockForList()
  //   	IncomingSession sess1 = new IncomingSession(phone:t1.phone,
  //   		numberAsString:"1112223333")
  //   	sess1.save(flush:true, failOnError:true)

  //   	when:
  //   	params.teamId = t1.id
  //   	request.method = "GET"
  //   	controller.index()
  //       List<Long> ids = TypeConversionUtils.allTo(Long, t1.phone.sessions*.id)

  //   	then:
  //       response.status == HttpServletResponse.SC_OK
  //       response.json.size() == ids.size()
  //       response.json*.id.every { ids.contains(it as Long) }
  //   }

  //   // Show
  //   // ----

  //   void "test show nonexistent id"() {
  //   	when:
  //   	params.id = "nonexistent"
  //   	request.method = "GET"
  //   	controller.show()

  //   	then:
  //   	response.status == HttpServletResponse.SC_NOT_FOUND
  //   }

  //   void "test show forbidden session"() {
  //   	given:
  //   	controller.authService = [
		// 	hasPermissionsForSession:{ Long id -> false }
		// ] as AuthService
		// IncomingSession sess1 = new IncomingSession(phone:t1.phone,
  //   		numberAsString:"1112223333")
  //   	sess1.save(flush:true, failOnError:true)

  //   	when:
  //   	params.id = sess1.id
  //   	request.method = "GET"
  //   	controller.show()

  //   	then:
  //       response.status == HttpServletResponse.SC_FORBIDDEN
  //   }

  //   void "test show"() {
  //   	given:
  //   	controller.authService = [
		// 	hasPermissionsForSession:{ Long id -> true }
		// ] as AuthService
		// IncomingSession sess1 = new IncomingSession(phone:t1.phone,
  //   		numberAsString:"1112223333")
  //   	sess1.save(flush:true, failOnError:true)

  //   	when:
  //   	params.id = sess1.id
  //   	request.method = "GET"
  //   	controller.show()

  //   	then:
  //   	response.status == HttpServletResponse.SC_OK
  //       response.json.id == sess1.id
  //   }

  //   // Save
  //   // ----

  //   void "test save without id"() {
  //   	given:
  //   	controller.authService = [getIsActive:{ -> true }] as AuthService
  //   	controller.sessionService = [createForStaff: { Map body ->
  //   		new Result(status:ResultStatus.CREATED, payload:body)
		// }] as SessionService

		// when:
		// request.json = "{'session':{ 'hello':'okay' }}"
		// request.method = "POST"
		// controller.save()

  //   	then: "implicit staff"
  //   	response.status == HttpServletResponse.SC_CREATED
  //       response.json.hello == "okay"
  //   }

  //   void "test save team id"() {
  //   	given:
  //   	controller.authService = [
  //   		exists:{ clazz, id -> true },
  //   		hasPermissionsForTeam:{ Long id -> true }
		// ] as AuthService
  //   	controller.sessionService = [createForTeam: { Long id, Map body ->
  //   		new Result(status:ResultStatus.CREATED, payload:body)
		// }] as SessionService

		// when:
		// request.json = "{'session':{ 'hello':'okay' }}"
		// request.method = "POST"
		// params.teamId = t1.id
		// controller.save()

  //   	then:
  //   	response.status == HttpServletResponse.SC_CREATED
  //       response.json.hello == "okay"
  //   }

  //   // Update
  //   // ------

  //   void "test update"() {
  //   	given:
  //   	controller.authService = [
  //   		exists:{ clazz, id -> true },
  //   		hasPermissionsForSession:{ Long id -> true }
		// ] as AuthService
  //   	controller.sessionService = [update: { Long id, Map body ->
  //   		new Result(status:ResultStatus.OK, payload:body)
		// }] as SessionService

		// when:
		// request.json = "{'session':{ 'hello':'okay' }}"
		// request.method = "PUT"
		// params.id = 123L
		// controller.update()

		// then:
		// response.status == HttpServletResponse.SC_OK
  //       response.json.hello == "okay"
  //   }

  //   // Delete
  //   // ------

  //   void "test delete"() {
  //   	when:
  //   	request.method = "DELETE"
  //   	controller.delete()

  //   	then:
  //   	response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
  //   }
}
