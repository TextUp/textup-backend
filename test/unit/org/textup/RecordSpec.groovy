package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import spock.lang.Shared
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt])
@TestMixin(HibernateTestMixin)
class RecordSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

	def setup() {
		ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
		fac.messageSource = [getMessage:{ String code, Object[] parameters, Locale locale ->
			code
		}] as MessageSource
	}

    void "test adding items to the record and deletion"() {
    	given: "a valid record"
    	Record rec = new Record()
    	rec.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	rec.save(flush:true, failOnError:true)

    	when: "we have a record"
    	assert rec.addText([contents:"hello"], null).success
    	assert rec.addCall([durationInSeconds:60], null).success
    	assert rec.add(new RecordText(contents:"hello2"), null).success
    	rec.save(flush:true, failOnError:true) //flush new record items

    	then:
    	RecordItem.countByRecord(rec) == 3
    	RecordText.countByRecord(rec) == 2
    	RecordCall.countByRecord(rec) == 1

    	when: "we add an invalid text item"
    	Result res = rec.addText(null, null)

    	then:
    	res.success == false
    	res.payload instanceof ValidationErrors
    	res.payload.errorCount == 1

    	when: "we add a missing item"
    	res = rec.add(null, null)

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "record.noRecordItem"
    }

    void "test lastRecordActivity is kept up to date"() {
        given: "a valid record"
        Record rec = new Record()
        rec.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
        rec.save(flush:true, failOnError:true)

        DateTime currentTimestamp = rec.lastRecordActivity

        when: "we add a text"
        assert rec.addText([contents:"hello"], null).success

        then: "lastRecordActivity is updated"
        rec.lastRecordActivity.isAfter(currentTimestamp)

        when: "we add a call"
        currentTimestamp = rec.lastRecordActivity
        assert rec.addCall([durationInSeconds:60], null).success

        then: "lastRecordActivity is updated"
        rec.lastRecordActivity.isAfter(currentTimestamp)
    }

    void "test retrieving items from the record"() {
    	given: "a record with items of various ages"
    	Record rec = new Record()
    	rec.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	rec.save(flush:true, failOnError:true)
    	RecordItem nowItem = rec.add(new RecordItem(), null).payload,
    		lWkItem = rec.add(new RecordItem(), null).payload,
    		yestItem = rec.add(new RecordItem(), null).payload,
    		twoDItem = rec.add(new RecordItem(), null).payload,
    		thrDItem = rec.add(new RecordItem(), null).payload
    	rec.save(flush:true, failOnError:true)
    	assert RecordItem.countByRecord(rec) == 5
    	//can't set the whenCreated in the constructor
    	lWkItem.whenCreated = DateTime.now().minusWeeks(1)
    	yestItem.whenCreated = DateTime.now().minusDays(1)
    	twoDItem.whenCreated = DateTime.now().minusDays(2)
    	thrDItem.whenCreated = DateTime.now().minusDays(3)
    	thrDItem.save(flush:true, failOnError:true)

    	when: "we get items between a date range"
    	List<RecordItem> items = rec.getBetween(DateTime.now().minusDays(4),
    		DateTime.now().minusHours(22), [max:2, offset:1])

    	then:
    	items.size() == 2
    	items[0] == twoDItem
    	items[1] == thrDItem

    	when: "we get items since a certain date"
    	items = rec.getSince(DateTime.now().minusWeeks(4), [max:3, offset:1])

    	then:
    	items.size() == 3
    	items[0] == yestItem
    	items[1] == twoDItem
    	items[2] == thrDItem
    }
}
