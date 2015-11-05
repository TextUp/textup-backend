package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([Record, RecordItem, RecordNote, RecordText, RecordCall, 
	RecordItemReceipt, PhoneNumber])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

	def setup() {
		ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
		fac.messageSource = [getMessage:{ String code, 
			Object[] parameters, Locale locale -> code }] as MessageSource
	}

    void "test adding items to the record and deletion"() {
    	when: "we have a record"
    	Record rec = new Record() 
    	rec.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	rec.save(flush:true)

    	assert rec.addText([contents:"hello"], null).success
    	assert rec.addNote([note:"i am a note"], null).success
    	assert rec.addCall([durationInSeconds:60], null).success
    	assert rec.add(new RecordText(contents:"hello2"), null).success
    	rec.save(flush:true) //flush new record items

    	then:
    	RecordItem.countByRecord(rec) == 4
    	RecordText.countByRecord(rec) == 2
    	RecordNote.countByRecord(rec) == 1
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
    	res.payload.code == "record.error.noRecordItem"

    	when: "we add multiple RecordItems"
    	rec.addAll([new RecordText(contents:"hello3"), new RecordNote(note:"what"), 
    		new RecordCall(durationInSeconds:88)], null)
    	rec.save(flush:true)
    	int totalNumItems = 7

    	then:
    	RecordItem.countByRecord(rec) == totalNumItems
    	RecordText.countByRecord(rec) == 3
    	RecordNote.countByRecord(rec) == 2
    	RecordCall.countByRecord(rec) == 2

    	when: "we delete the record"
    	int itemBaseline = RecordItem.count(), 
    		recBaseline = Record.count()
    	rec.delete(flush:true)

    	then: 
    	RecordItem.count() == itemBaseline - totalNumItems
    	Record.count() == recBaseline - 1
    }

    void "test editing note items in the record"() {
    	given: "we have a record with some notes"
    	Record rec = new Record()
    	rec.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	rec.save(flush:true)
    	RecordNote editable = rec.addNote([note:"edit me"], null).payload, 
    		uneditable = rec.addNote([note:"back off", editable:false], null).payload
    	uneditable.save(flush:true)

    	when: "we try to edit a missing note"
    	String msg = "hi"
    	Result res = rec.editNote(-123, [note:msg], null)

    	then: 
    	res.success == false 
    	res.payload instanceof Map
    	res.payload.code == "record.error.noteNotFound"

    	when: "we try to edit an editable note"
    	res = rec.editNote(editable.id, [note:msg], null)

    	then:
    	res.success == true 
    	res.payload instanceof RecordNote
    	res.payload.note == msg

    	when: "we try to edit a uneditable note"
    	res = rec.editNote(uneditable.id, [note:msg], null)

    	then: 
    	res.success == false 
    	res.payload instanceof Map
    	res.payload.code == "record.error.noteUneditable"
    }

    void "test retrieving items from the record"() {
    	given: "a record with items of various ages"
    	Record rec = new Record() 
    	rec.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	rec.save(flush:true)
    	RecordItem nowItem = rec.add(new RecordItem(), null).payload, 
    		lWkItem = rec.add(new RecordItem(), null).payload, 
    		yestItem = rec.add(new RecordItem(), null).payload,
    		twoDItem = rec.add(new RecordItem(), null).payload,
    		thrDItem = rec.add(new RecordItem(), null).payload
    	rec.save(flush:true)
    	assert RecordItem.countByRecord(rec) == 5
    	//can't set the dateCreated in the constructor
    	lWkItem.dateCreated = DateTime.now().minusWeeks(1)
    	yestItem.dateCreated = DateTime.now().minusDays(1)
    	twoDItem.dateCreated = DateTime.now().minusDays(2)
    	thrDItem.dateCreated = DateTime.now().minusDays(3)
    	thrDItem.save(flush:true)

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
