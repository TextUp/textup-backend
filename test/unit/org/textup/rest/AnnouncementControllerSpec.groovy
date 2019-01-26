package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
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
@TestFor(AnnouncementController)
@TestMixin(HibernateTestMixin)
class AnnouncementControllerSpec extends CustomSpec {

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
  //   	FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
  //   		message:"1112223333", expiresAt:DateTime.now().plusMinutes(30))
  //   	announce.save(flush:true, failOnError:true)

  //   	when:
  //   	request.method = "GET"
  //   	controller.index()
  //   	Staff loggedIn = Staff.findByUsername(loggedInUsername)
  //       List<Long> ids = TypeConversionUtils.allTo(Long, loggedIn.phone.announcements*.id)

  //   	then: "implicit staff"
  //       response.status == HttpServletResponse.SC_OK
  //       response.json.size() == ids.size()
  //       response.json*.id.every { ids.contains(it as Long) }
  //   }

  //   void "test with team id"() {
  //   	given:
  //   	mockForList()
  //   	FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:t1.phone,
  //   		message:"1112223333", expiresAt:DateTime.now().plusMinutes(30))
  //   	announce.save(flush:true, failOnError:true)

  //   	when:
  //   	params.teamId = t1.id
  //   	request.method = "GET"
  //   	controller.index()
  //       List<Long> ids = TypeConversionUtils.allTo(Long, t1.phone.announcements*.id)

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

  //   void "test show forbidden"() {
  //   	given:
  //   	controller.authService = [
		// 	hasPermissionsForAnnouncement:{ Long id -> false }
		// ] as AuthService
		// FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:t1.phone,
  //   		message:"1112223333", expiresAt:DateTime.now().plusMinutes(30))
  //   	announce.save(flush:true, failOnError:true)

  //   	when:
  //   	params.id = announce.id
  //   	request.method = "GET"
  //   	controller.show()

  //   	then:
  //       response.status == HttpServletResponse.SC_FORBIDDEN
  //   }

  //   void "test show"() {
  //   	given:
  //   	controller.authService = [
		// 	hasPermissionsForAnnouncement:{ Long id -> true }
		// ] as AuthService
		// FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:t1.phone,
  //   		message:"1112223333", expiresAt:DateTime.now().plusMinutes(30))
  //   	announce.save(flush:true, failOnError:true)

  //   	when:
  //   	params.id = announce.id
  //   	request.method = "GET"
  //   	controller.show()

  //   	then:
  //   	response.status == HttpServletResponse.SC_OK
  //       response.json.id == announce.id
  //   }

  //   // Save
  //   // ----

  //   void "test save no id"() {
  //   	given:
  //   	controller.authService = [getIsActive:{ -> true }] as AuthService
  //   	controller.announcementService = [createForStaff: { Map body ->
  //   		new Result(success:true, payload:body, status:ResultStatus.CREATED)
		// }] as AnnouncementService

		// when:
		// request.json = "{'announcement':{ 'hello':'okay' }}"
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
  //   	controller.announcementService = [createForTeam: { Long id, Map body ->
  //   		new Result(success:true, payload:body, status:ResultStatus.CREATED)
		// }] as AnnouncementService

		// when:
		// request.json = "{'announcement':{ 'hello':'okay' }}"
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
  //   		hasPermissionsForAnnouncement:{ Long id -> true }
		// ] as AuthService
  //   	controller.announcementService = [update: { Long id, Map body ->
  //   		new Result(success:true, payload:body)
		// }] as AnnouncementService

		// when:
		// request.json = "{'announcement':{ 'hello':'okay' }}"
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
