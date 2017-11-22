package org.textup

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.xml.MarkupBuilder
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.type.OrgStatus
import org.textup.util.CustomSpec
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(SuperController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, StaffRole, Role, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class SuperControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    private String _username, _token

    def setup() {
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
    }
    def cleanup() {
        cleanupData()
    }

    // Page handlers
    // -------------

    void "test update passwords, passwords do not match"() {
    	when:
    	params.password = "testing"
    	params.confirmPassword = "kikibai"
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
    	params.password = pwd
    	params.confirmPassword = pwd
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
