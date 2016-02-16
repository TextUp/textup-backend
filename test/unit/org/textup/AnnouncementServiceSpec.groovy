package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@TestFor(AnnouncementService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    IncomingSession, FeaturedAnnouncement, AnnouncementReceipt])
@TestMixin(HibernateTestMixin)
class AnnouncementServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.authService = [getLoggedInAndActive:{
        	s1
    	}] as AuthService
    	Phone.metaClass.sendAnnouncement = { String message,
        	DateTime expiresAt, Staff staff ->
        	new Result(type:ResultType.SUCCESS, success:true, payload:null)
    	}
    }

    def cleanup() {
        super.cleanupData()
    }

    void "test create"() {
    	when: "no phone"
    	Result res = service.create(null, [:])

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == UNPROCESSABLE_ENTITY
    	res.payload.code == "announcementService.create.noPhone"

    	when: "success, having mocked method on phone"
    	res = service.create(p1, [message: "hi!", expiresAt:DateTime.now().toDate()])

    	then:
    	res.success == true
    }

    void "test update"() {
    	given: "baselines and existing announcement"
    	FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
    		message:"hello there bud!", expiresAt:DateTime.now().plusDays(2))
    	announce.save(flush:true, failOnError:true)
    	int aBaseline = FeaturedAnnouncement.count()

    	when: "nonexistent id"
    	Result res = service.update(-88L, [:])

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == NOT_FOUND
    	res.payload.code == "announcementService.update.notFound"

    	when: "invalid expires at"
    	res = service.update(announce.id, [expiresAt:"invalid"])

    	then:
    	res.success == false
    	res.type == ResultType.VALIDATION
    	res.payload.errorCount == 1

    	when: "valid"
    	DateTime newExpires = DateTime.now().plusMinutes(30)
    	res = service.update(announce.id, [expiresAt:newExpires.toDate()])

    	then:
    	res.success == true
    	res.payload instanceof FeaturedAnnouncement
    	res.payload.expiresAt == newExpires
    }
}
