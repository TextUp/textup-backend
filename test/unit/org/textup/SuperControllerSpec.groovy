package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.xml.MarkupBuilder
import javax.servlet.http.HttpServletRequest
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.springframework.security.authentication.encoding.PasswordEncoder
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(SuperController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, StaffRole, Role, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class SuperControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    boolean _passwordValidOutcome

    def setup() {
        _passwordValidOutcome = true
        setupData()
        controller.springSecurityService = [
        	getCurrentUser:{ Staff.findByUsername(loggedInUsername) },
        	reauthenticate:{ String un -> }
        ] as SpringSecurityService
        controller.mailService = [
        	notifyRejection:{ Staff s1 ->
        		new Result(status:ResultStatus.OK, payload:null)
    		},
			notifyApproval:{ Staff s1 ->
				new Result(status:ResultStatus.OK, payload:null)
			},
        ] as MailService
        controller.passwordEncoder = [
            isPasswordValid:{ String encPass, String rawPass, Object salt  ->
                _passwordValidOutcome
            }
        ] as PasswordEncoder
    }
    def cleanup() {
        cleanupData()
    }

    // Page handlers
    // -------------

    void "test not updating password"() {
        when: "none of the password fields are filled out"
        controller.updateSettings()

        then: "other fields can still be filled out"
        response.redirectUrl == "/super/settings"
        flash.messages instanceof List
        flash.messages.size() == 1
        flash.messages[0] == "Successfully updated settings."
    }

    void "test updating passwords missing current password"() {
        when: "missing current password"
        params.newPassword = "testing"
        params.confirmNewPassword = "testing"
        controller.updateSettings()

        then: "cannot update password"
        response.redirectUrl == "/super/settings"
        flash.messages instanceof List
        flash.messages.size() == 1
        flash.messages[0] == "Could not update password. Current password is either blank or incorrect."
    }

    void "test updating passwords incorrect current password"() {
        given:
        _passwordValidOutcome = false

        when: "incorrect current password"
        params.currentPassword = "invalid blah blah blah"
        params.newPassword = "testing"
        params.confirmNewPassword = "testing"
        controller.updateSettings()

        then: "cannot update password"
        response.redirectUrl == "/super/settings"
        flash.messages instanceof List
        flash.messages.size() == 1
        flash.messages[0] == "Could not update password. Current password is either blank or incorrect."
    }

    void "test update passwords, passwords do not match"() {
    	when:
        params.currentPassword = loggedInPassword
    	params.newPassword = "testing"
    	params.confirmNewPassword = "kikibai"
    	controller.updateSettings()

    	then:
    	response.redirectUrl == "/super/settings"
    	flash.messages != null
    }

    void "test update passwords, success"() {
    	given:
    	String pwd = "iAmANewSuperSafePassword"
    	String un = "IAmANewUsername"

    	when:
        params.currentPassword = loggedInPassword
    	params.newPassword = pwd
    	params.confirmNewPassword = pwd
    	params.username = un
    	controller.updateSettings()
    	Staff.withSession { it.flush() }
    	Staff loggedIn = Staff.findByUsername(loggedInUsername)

    	then:
    	Staff.findByUsername(loggedInUsername) == null
    	Staff.findByUsername(un.toLowerCase()) != null
    	Staff.findByUsername(un.toLowerCase()).password == pwd
    	response.redirectUrl == "/super/settings"
    	flash.messages != null
    }

    // Actions
    // -------

    void "test reject org, not found"() {
    	given:
    	org.status = OrgStatus.PENDING
    	org.save(flush:true, failOnError:true)

    	when:
    	params.id = "nonexistent"
    	controller.rejectOrg()

    	then:
    	org.status != OrgStatus.REJECTED
    	response.redirectUrl == "/super/index"
    	flash.messages != null
    }

    void "test reject org, success"() {
    	given:
    	org.status = OrgStatus.PENDING
    	org.save(flush:true, failOnError:true)

    	when:
    	params.id = org.id
    	controller.rejectOrg()
    	Organization.withSession { it.flush() }
    	org.refresh()

    	then:
    	org.status == OrgStatus.REJECTED
    	response.redirectUrl == "/super/index"
    	flash.messages != null
    }

    void "test approve org, not found"() {
    	given:
    	org.status = OrgStatus.PENDING
    	org.save(flush:true, failOnError:true)

    	when:
    	params.id = "nonexistent"
    	controller.approveOrg()

    	then:
    	org.status != OrgStatus.APPROVED
    	response.redirectUrl == "/super/index"
    	flash.messages != null
    }

    void "test approve org, success"() {
    	given:
    	org.status = OrgStatus.PENDING
    	org.save(flush:true, failOnError:true)

    	when:
    	params.id = org.id
    	controller.approveOrg()
    	Organization.withSession { it.flush() }
    	org.refresh()

    	then:
    	org.status == OrgStatus.APPROVED
    	response.redirectUrl == "/super/index"
    	flash.messages != null
    }
}
