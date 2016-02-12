package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.types.OrgStatus
import org.textup.types.ResultType
import org.textup.types.StaffStatus
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(PasswordResetService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
	Schedule, Location, WeeklySchedule, PhoneOwnership, PasswordResetToken, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class PasswordResetServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
    	setupData()
	    service.grailsApplication = [getFlatConfig:{
			["textup.resetTokenSize":10]
		}] as GrailsApplication
		service.resultFactory = getResultFactory()
		service.mailService = [notifyPasswordReset: { Staff s1, String token ->
			new Result(type:ResultType.SUCCESS, success:true, payload:token)
		}] as MailService
    }

    def cleanup() {
    	cleanupData()
    }

    @FreshRuntime
    void "test requesting password reset"() {
    	when: "nonexisting username"
    	Result res = service.requestReset("invalid")

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == NOT_FOUND
    	res.payload.code == "passwordResetService.requestReset.staffNotFound"

    	when:
    	res = service.requestReset(s1.username)

    	then:
    	res.success == true
    	PasswordResetToken.count() == 1
    	PasswordResetToken.list()[0].token == res.payload
    	PasswordResetToken.list()[0].toBeResetId == s1.id
    }

    void "test completing password reset"() {
    	given: "tokens"
    	PasswordResetToken tok1 = new PasswordResetToken(toBeResetId:s1.id,
    			token:"superSecretToken"),
    		expiredTok = new PasswordResetToken(toBeResetId:s1.id, token:"blah",
    			expires:DateTime.now().minusDays(1))
    	[tok1, expiredTok]*.save(flush:true, failOnError:true)

    	when: "request with invalid token"
    	Result<Staff> res = service.resetPassword("whatisthis", "password")

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == NOT_FOUND
    	res.payload.code == "passwordResetService.resetPassword.tokenNotFound"

    	when: "request with valid token but expired"
    	res = service.resetPassword(expiredTok.token, "password")

    	then:
    	res.success == false
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == BAD_REQUEST
    	res.payload.code == "passwordResetService.resetPassword.tokenExpired"

    	when: "valid token, invalid password"
    	res = service.resetPassword(tok1.token, "")

    	then: "token not yet expired"
    	res.success == false
    	res.type == ResultType.VALIDATION
    	PasswordResetToken.findByToken(tok1.token).isExpired == false

    	when:
    	String pwd = "iamsospecial!!!!"
    	res = service.resetPassword(tok1.token, pwd)

    	then:
    	res.success == true
    	res.payload instanceof Staff
    	res.payload.password == pwd
    	PasswordResetToken.findByToken(tok1.token).isExpired == true
    }
}
