package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.json.JsonBuilder
import groovy.xml.MarkupBuilder
import java.util.UUID
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.textup.*
import org.textup.types.ReceiptStatus
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(NotifyController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class NotifyControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "test claim notification success"() {
    	given: "show notification success"
    	Map data = [key:UUID.randomUUID().toString()]
    	controller.tokenService = [showNotification:{ String token ->
    		new Result(success:true, payload:data)
		}] as TokenService

    	when:
    	request.method = "GET"
        params.id = "token goes here"
        controller.show()

    	then:
    	response.status == SC_OK
    	response.json != null
    	response.json.key == data.key
    }

    void "test claim notification error"() {
    	given: "show notification failure"
    	Map payload = [status:HttpStatus.BAD_REQUEST, message:"testing123"]
    	controller.tokenService = [showNotification:{ String token ->
    		new Result(success:false, type:ResultType.MESSAGE_STATUS,
    			payload:payload)
		}] as TokenService

    	when:
    	request.method = "GET"
        params.id = "token goes here"
        controller.show()

    	then:
    	response.status == payload.status.value()
    	response.json != null
    	response.json.errors instanceof List
    	response.json.errors.size() == 1
    	response.json.errors[0].message == payload.message
    }
}
