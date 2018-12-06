package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.test.*
import org.textup.type.*
import org.textup.validator.EmailEntity
import spock.lang.Shared
import spock.lang.Specification

@TestFor(MailService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
	Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class MailServiceSpec extends CustomSpec {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    Map flatConfig

    def setup() {
    	setupData()

        flatConfig = config.flatten()
        service.grailsApplication.flatConfig = flatConfig
    	service.metaClass.sendMail = { EmailEntity to, EmailEntity from, String templateId,
            Map<String, String> data ->
	    	new Result(status:ResultStatus.OK, payload:[
	    		to:to,
	    		from:from,
	    		templateId:templateId,
                data: data
    		])
	    }
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
		res.payload.from.name == flatConfig["textup.mail.standard.name"]
		res.payload.from.email == flatConfig["textup.mail.standard.email"]
		res.payload.templateId == flatConfig["textup.apiKeys.sendGrid.templateIds.passwordReset"]
        res.payload.data.name == s1.name
        res.payload.data.username == s1.username
        res.payload.data.link == flatConfig["textup.links.passwordReset"] + token
    }

    void "test pending new organization"() {
    	when:
    	Result res = service.notifyAboutPendingOrg(org)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload.to.validate()
        res.payload.to.name == flatConfig["textup.mail.self.name"]
        res.payload.to.email == flatConfig["textup.mail.self.email"]
    	res.payload.from.validate()
		res.payload.from.name == flatConfig["textup.mail.standard.name"]
        res.payload.from.email == flatConfig["textup.mail.standard.email"]
		res.payload.templateId == flatConfig["textup.apiKeys.sendGrid.templateIds.pendingOrg"]
        res.payload.data.org == org.name
        res.payload.data.link == flatConfig["textup.links.superDashboard"]
    }

    void "testing notifying of pending staff"() {
        when: "no admins"
        Result res = service.notifyAboutPendingStaff(s3, [])

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "mailService.notifyAboutPendingStaff.noAdmins"

        when:
        res = service.notifyAboutPendingStaff(s3, [s1, s2])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload.size() == 2
        res.payload[0].to.validate()
        res.payload[0].to.name == s1.name
        res.payload[0].to.email == s1.email
        res.payload[0].from.validate()
        res.payload[0].from.name == flatConfig["textup.mail.standard.name"]
        res.payload[0].from.email == flatConfig["textup.mail.standard.email"]
        res.payload[0].templateId == flatConfig["textup.apiKeys.sendGrid.templateIds.pendingStaff"]
        res.payload[0].data.staff == s3.name
        res.payload[0].data.org == s3.org.name
        res.payload[0].data.link == flatConfig["textup.links.adminDashboard"]
        // just check the email recipient, everything else should be the same
        res.payload[1].to.validate()
        res.payload[1].to.name == s2.name
        res.payload[1].to.email == s2.email
    }

    void "test sending new user invitation"() {
        when:
        String pwd = "i am a password"
        String code = "i am a lock code"
        Result res = service.notifyInvitation(s1, s2, pwd, code)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload.to.validate()
        res.payload.to.name == s2.name
        res.payload.to.email == s2.email
        res.payload.from.validate()
        res.payload.from.name == flatConfig["textup.mail.standard.name"]
        res.payload.from.email == flatConfig["textup.mail.standard.email"]
        res.payload.templateId == flatConfig["textup.apiKeys.sendGrid.templateIds.invited"]
        res.payload.data.inviter == s1.name
        res.payload.data.invitee == s2.name
        res.payload.data.username == s2.username
        res.payload.data.password == pwd
        res.payload.data.lockCode == code
        res.payload.data.link == flatConfig["textup.links.setupAccount"]
    }

    void "test sending approval"() {
        when:
        Result res = service.notifyApproval(s1)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload.to.validate()
        res.payload.to.name == s1.name
        res.payload.to.email == s1.email
        res.payload.from.validate()
        res.payload.from.name == flatConfig["textup.mail.standard.name"]
        res.payload.from.email == flatConfig["textup.mail.standard.email"]
        res.payload.templateId == flatConfig["textup.apiKeys.sendGrid.templateIds.approved"]
        res.payload.data.name == s1.name
        res.payload.data.username == s1.username
        res.payload.data.org == s1.org.name
        res.payload.data.link == flatConfig["textup.links.setupAccount"]
    }

    void "test sending rejection"() {
        when:
        Result res = service.notifyRejection(s1)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload.to.validate()
        res.payload.to.name == s1.name
        res.payload.to.email == s1.email
        res.payload.from.validate()
        res.payload.from.name == flatConfig["textup.mail.standard.name"]
        res.payload.from.email == flatConfig["textup.mail.standard.email"]
        res.payload.templateId == flatConfig["textup.apiKeys.sendGrid.templateIds.rejected"]
        res.payload.data.name == s1.name
        res.payload.data.username == s1.username
    }
}
