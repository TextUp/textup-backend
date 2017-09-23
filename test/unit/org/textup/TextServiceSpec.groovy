package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.springframework.context.MessageSource
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import spock.lang.Shared
import spock.lang.Specification

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
@TestFor(TextService)
class TextServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    private _sid = "superSecretSid"

    def setup() {
    	service.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	service.resultFactory.messageSource = [getMessage:{ String c, Object[] p, Locale l ->
            c
        }] as MessageSource
    	service.metaClass.tryText = { BasePhoneNumber fromNum, BasePhoneNumber toNum,
	        String message ->
	        new Result(status:ResultStatus.OK, payload:[sid:_sid])
	    }
    }

    void "test send"() {
    	when: "send to no recipients"
    	PhoneNumber fromNum = new PhoneNumber(number:"1112223333")
    	assert fromNum.validate()
    	String msg = "hello there!!"
    	Result res = service.send(fromNum, [], msg)

    	then:
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0] == "textService.text.allFailed"

    	when: "stop on first success"
    	_sid = "I am so secret!!"
    	PhoneNumber toNum1 = new PhoneNumber(number:"1238943239"),
    		toNum2 = new PhoneNumber(number:"1220943239")
		assert toNum1.validate() && toNum2.validate()
    	res = service.send(fromNum, [toNum1, toNum2], msg)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload instanceof TempRecordReceipt
    	res.payload.receivedByAsString == toNum1.number
    	res.payload.apiId == _sid
    }
}
