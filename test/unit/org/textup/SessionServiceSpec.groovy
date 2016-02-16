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

@TestFor(SessionService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    IncomingSession, FeaturedAnnouncement, AnnouncementReceipt])
@TestMixin(HibernateTestMixin)
class SessionServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.authService = [getLoggedInAndActive:{
			s1
    	}] as AuthService
    }

    def cleanup() {
        super.cleanupData()
    }

    void "test create"() {
    	given: "baselines"
    	int sBaseline = IncomingSession.count()

    	when: "without phone"
    	Result res = service.create(null, [:])

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == UNPROCESSABLE_ENTITY
    	res.payload.code == "sessionService.create.noPhone"

    	when: "invalid number"
		res = service.create(p1, [number:"invalid"])

    	then:
    	res.success == false
    	res.type == ResultType.VALIDATION
    	res.payload.errorCount == 1

    	when: "all valid"
    	Map body = [number:"1112223333"]
    	assert IncomingSession.findByNumberAsStringAndPhone(body.number, p1) == null
    	res = service.create(p1, body)

    	then:
    	res.success == true
    	res.payload instanceof IncomingSession
    	res.payload.numberAsString == body.number
    	IncomingSession.count() == sBaseline + 1

    	when: "same number"
    	res = service.create(p1, body)

    	then: "merge"
    	res.success == true
    	res.payload instanceof IncomingSession
    	res.payload.numberAsString == body.number
    	IncomingSession.count() == sBaseline + 1
    }

    void "test update"() {
    	given: "baselines"
    	String num = "2349302930"
    	assert IncomingSession.findByNumberAsStringAndPhone(num, p1) == null
    	IncomingSession sess1 = new IncomingSession(phone:p1, numberAsString:num)
    	sess1.save(flush:true, failOnError:true)
    	int sBaseline = IncomingSession.count()

    	when: "nonexistent id"
    	Result res = service.update(-88L, [:])

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == NOT_FOUND
    	res.payload.code == "sessionService.update.notFound"

    	when: "existing id, invalid"
    	res = service.update(sess1.id, [isSubscribedToText:"hello"])

    	then:
    	res.success == false
    	res.type == ResultType.VALIDATION
    	res.payload.errorCount == 1

    	when: "existing id, valid"
    	res = service.update(sess1.id, [isSubscribedToText:true])

    	then:
    	res.success == true
    	res.payload instanceof IncomingSession
    	res.payload.isSubscribedToText == true
    	IncomingSession.count() == sBaseline
    }
}
