package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.textup.util.CustomSpec
import spock.lang.Shared

@TestFor(AnnouncementService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    IncomingSession, FeaturedAnnouncement, AnnouncementReceipt, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class AnnouncementServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.authService = [getLoggedInAndActive:{ s1 }] as AuthService
    	Phone.metaClass.sendAnnouncement = { String message,
        	DateTime expiresAt, Staff staff ->
        	new Result(status:ResultStatus.OK, payload:null)
    	}
    }

    def cleanup() {
        super.cleanupData()
    }

    void "test create"() {
        given:
        addToMessageSource("announcementService.create.noPhone")

    	when: "no phone"
    	Result<FeaturedAnnouncement> res = service.create(null, [:])

    	then:
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0] == "announcementService.create.noPhone"

    	when: "success, having mocked method on phone"
    	res = service.create(p1, [message: "hi!", expiresAt:DateTime.now().toDate()])

    	then:
    	res.success == true
        res.status == ResultStatus.CREATED
    }

    void "test update"() {
    	given: "baselines and existing announcement"
    	FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
    		message:"hello there bud!", expiresAt:DateTime.now().plusDays(2))
    	announce.save(flush:true, failOnError:true)
    	int aBaseline = FeaturedAnnouncement.count()
        addToMessageSource("announcementService.update.notFound")

    	when: "nonexistent id"
    	Result<FeaturedAnnouncement> res = service.update(-88L, [:])

    	then:
    	res.success == false
    	res.status == ResultStatus.NOT_FOUND
    	res.errorMessages[0] == "announcementService.update.notFound"

    	when: "invalid expires at"
        service.resultFactory.messageSource = mockMessageSourceWithResolvable()
    	res = service.update(announce.id, [expiresAt:"invalid"])

    	then:
    	res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0].contains("nullable")

    	when: "valid"
    	DateTime newExpires = DateTime.now().plusMinutes(30)
    	res = service.update(announce.id, [expiresAt:newExpires.toDate()])

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload instanceof FeaturedAnnouncement
    	res.payload.expiresAt == newExpires
    }
}
