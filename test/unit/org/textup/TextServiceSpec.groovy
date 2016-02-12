package org.textup

import grails.test.mixin.TestFor
import org.springframework.context.MessageSource
import org.textup.types.ResultType
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

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
	        new Result(type:ResultType.SUCCESS, success:true, payload:[sid:_sid])
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
    	res.type == ResultType.MESSAGE_STATUS
    	res.payload.status == UNPROCESSABLE_ENTITY
    	res.payload.code == "textService.text.allFailed"

    	when: "stop on first success"
    	_sid = "I am so secret!!"
    	PhoneNumber toNum1 = new PhoneNumber(number:"1238943239"),
    		toNum2 = new PhoneNumber(number:"1220943239")
		assert toNum1.validate() && toNum2.validate()
    	res = service.send(fromNum, [toNum1, toNum2], msg)

    	then:
    	res.success == true
    	res.payload instanceof TempRecordReceipt
    	res.payload.receivedByAsString == toNum1.number
    	res.payload.apiId == _sid
    }
}
