package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import java.util.UUID
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.context.MessageSource
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

// Because the CallService is so heavily dependent on callbacks,
// we mock the `doCall` method in this test class to allow for greater
// visibility into testing how the callback maps are passed.
// To test error checking, for making calls initially, see
// `CallServiceNoMockSpec.groovy`

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt])
@TestMixin(HibernateTestMixin)
@TestFor(CallService)
class CallServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
    	service.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	service.resultFactory.messageSource = [getMessage:{ String c, Object[] p, Locale l ->
            c
        }] as MessageSource
        service.grailsLinkGenerator = [link: { Map m ->
        	(m.params ?: [:]).toString()
    	}] as LinkGenerator
    	service.metaClass.doCall = { PhoneNumber fromNum, BasePhoneNumber toNum,
            Map afterPickup, String callback ->
	        new Result(status:ResultStatus.OK, payload:[afterPickup:afterPickup, callback:callback])
	    }
    }

    void "test starting a call to one or more numbers"() {
    	given:
    	Map afterPickup = [id:UUID.randomUUID().toString()]
    	PhoneNumber fromNum = new PhoneNumber(number:"1112223333"),
    		toNum1 = new PhoneNumber(number:"1112223334"),
    		toNum2 = new PhoneNumber(number:"1112223335")

    	when: "we start a call with one 'to' number"
    	Map callback = [handle:Constants.CALLBACK_STATUS]
    	Result res = service.start(fromNum, toNum1, afterPickup)

    	then:
    	res.success == true
    	res.payload.afterPickup.toString() == afterPickup.toString()
    	res.payload.callback == callback.toString()

    	when: "we start a call with multiple 'to' numbers"
    	callback = [handle:Constants.CALLBACK_STATUS,
    		remaining:[toNum2.e164PhoneNumber],
    		afterPickup:Helpers.toJsonString(afterPickup)]
    	res = service.start(fromNum, [toNum1, toNum2], afterPickup)

    	then:
    	res.success == true
    	res.payload.afterPickup.toString() == afterPickup.toString()
    	res.payload.callback == callback.toString()
    }
    @FreshRuntime
    void "test retrying a failed call"() {
    	given:
    	String existingApiId = UUID.randomUUID().toString()
    	String newApiId = UUID.randomUUID().toString()
    	PhoneNumber fromNum = new PhoneNumber(number:"1112223333"),
    		toNum1 = new PhoneNumber(number:"1112223334"),
    		toNum2 = new PhoneNumber(number:"1112223335")
    	// populate db with receipts that have existing apiId
    	Record rec = new Record()
    	rec.save(flush:true, failOnError:true)
    	RecordItem rItem1 = new RecordItem(record:rec)
    	RecordItem rItem2 = new RecordItem(record:rec)
    	[rItem1, rItem2].each { RecordItem rItem ->
    		rItem.addToReceipts(apiId:existingApiId, receivedByAsString:"2223338888")
    		rItem.save(flush:true, failOnError:true)
    	}
    	// baselines
    	int iBaseline = RecordItem.count(),
    		rBaseline = RecordItemReceipt.count()
    	String receivedByString = "2223338888"
    	// override start method to limit scope of the test
    	service.metaClass.start = { PhoneNumber fNum,
        	List<? extends BasePhoneNumber> tNums, Map afterPickup ->
	        new Result(status:ResultStatus.OK,
	        	payload:new TempRecordReceipt(apiId:newApiId, receivedByAsString:receivedByString))
	    }

    	when:
    	Result<TempRecordReceipt> res = service.retry(fromNum, [toNum2], existingApiId, [:])

    	then:
    	res.success == true
    	res.payload instanceof TempRecordReceipt
    	res.payload.apiId == newApiId
    	RecordItem.count() == iBaseline
    	RecordItemReceipt.count() == rBaseline + 2
    	RecordItem.get(rItem1.id).receipts*.apiId.contains(newApiId)
    	RecordItem.get(rItem2.id).receipts*.apiId.contains(newApiId)
    }
}
