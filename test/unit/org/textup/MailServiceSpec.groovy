package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Shared
import spock.lang.Specification
import grails.plugin.springsecurity.SpringSecurityService
import org.textup.util.CustomSpec
import org.textup.type.StaffStatus
import org.textup.type.OrgStatus
import org.textup.validator.EmailEntity

@TestFor(MailService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
	Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class MailServiceSpec extends CustomSpec {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String stdEmailKey = "textup.mail.standard.email",
    	stdEmail = "ok@example.com",
    	selfEmailKey = "textup.mail.self.email",
    	selfEmail = "self@self.com"

    def setup() {
    	setupData()
    	service.metaClass.sendMail = { EmailEntity to, EmailEntity from, String subject,
	    	String contents, String templateId=null ->
	    	new Result(status:ResultStatus.OK, payload:[
	    		to:to,
	    		from:from,
	    		subject:subject,
	    		contents:contents,
	    		templateId:templateId
    		])
	    }
	    service.metaClass.config = { String key ->
	    	(key == stdEmailKey) ? stdEmail : ((key == selfEmailKey) ? selfEmail : key)
	    }
	    service.messageSource = [getMessage:{ String c, Object[] p, Locale l ->
            c
        }] as MessageSource
    }

    def cleanup() {
    	cleanupData()
    }

    void "test password reset"() {
    	when:
    	String token = "secretPasswordToken"
    	Result res = service.notifyPasswordReset(s1, token)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.to.validate()
    	res.payload.to.name == s1.name
    	res.payload.to.email == s1.email
    	res.payload.from.validate()
		res.payload.from.name == "textup.mail.standard.name"
		res.payload.from.email == stdEmail
		res.payload.subject == "mail.passwordReset.subject"
		res.payload.contents == "mail.passwordReset.body"
		res.payload.templateId == null
    }

    void "test signup with new organization"() {
    	when:
    	Result res = service.notifySuperOfNewOrganization("orgName")

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.to.validate()
    	res.payload.to.name == "textup.mail.self.name"
    	res.payload.to.email == selfEmail
    	res.payload.from.validate()
		res.payload.from.name == "textup.mail.standard.name"
		res.payload.from.email == stdEmail
		res.payload.subject == "mail.newOrganizationForSuper.subject"
		res.payload.contents == "mail.newOrganizationForSuper.body"
		res.payload.templateId == null

    	when:
    	res = service.notifyNewOrganizationOfApproval(s1)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.to.validate()
    	res.payload.to.name == s1.name
    	res.payload.to.email == s1.email
    	res.payload.from.validate()
		res.payload.from.name == "textup.mail.standard.name"
		res.payload.from.email == stdEmail
		res.payload.subject == "mail.approveForNewOrg.subject"
		res.payload.contents == "mail.approveForNewOrg.body"
		res.payload.templateId == null

    	when:
    	res = service.notifyNewOrganizationOfRejection(s1)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.to.validate()
    	res.payload.to.name == s1.name
    	res.payload.to.email == s1.email
    	res.payload.from.validate()
		res.payload.from.name == "textup.mail.standard.name"
		res.payload.from.email == stdEmail
		res.payload.subject == "mail.rejectForNewOrg.subject"
		res.payload.contents == "mail.rejectForNewOrg.body"
		res.payload.templateId == null
    }

    void "test signup with existing organization"() {
    	when: "no admins"
        // result factory messageSource is the StaticMessageSource managed by
        // CustomSpec. This is a distinct implementation from the mock used
        // to represent the service's MessageSource
        addToMessageSource("mailService.notifyAdminsOfPendingStaff.noAdmins")
    	Result res = service.notifyAdminsOfPendingStaff("pendingName", [])

    	then:
    	res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "mailService.notifyAdminsOfPendingStaff.noAdmins"

    	when:
    	res = service.notifyAdminsOfPendingStaff("pending", [s1, s2])

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.size() == 2
    	res.payload[0].to.validate()
    	res.payload[0].to.name == s1.name
    	res.payload[0].to.email == s1.email
    	res.payload[0].from.validate()
		res.payload[0].from.name == "textup.mail.standard.name"
		res.payload[0].from.email == stdEmail
		res.payload[0].subject == "mail.pendingForAdmin.subject"
		res.payload[0].contents == "mail.pendingForAdmin.body"
		res.payload[0].templateId == null
		res.payload[1].to.validate()
    	res.payload[1].to.name == s2.name
    	res.payload[1].to.email == s2.email

    	when:
    	res = service.notifyPendingOfApproval(s1)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.to.validate()
    	res.payload.to.name == s1.name
    	res.payload.to.email == s1.email
    	res.payload.from.validate()
		res.payload.from.name == "textup.mail.standard.name"
		res.payload.from.email == stdEmail
		res.payload.subject == "mail.approveForPending.subject"
		res.payload.contents == "mail.approveForPending.body"
		res.payload.templateId == null

    	when:
    	res = service.notifyPendingOfRejection(s1)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.to.validate()
    	res.payload.to.name == s1.name
    	res.payload.to.email == s1.email
    	res.payload.from.validate()
		res.payload.from.name == "textup.mail.standard.name"
		res.payload.from.email == stdEmail
		res.payload.subject == "mail.rejectForPending.subject"
		res.payload.contents == "mail.rejectForPending.body"
		res.payload.templateId == null
    }
}
