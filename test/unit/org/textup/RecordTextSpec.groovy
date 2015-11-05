package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([Record, RecordItem, RecordNote, RecordText, RecordCall, 
	RecordItemReceipt, PhoneNumber])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordTextSpec extends Specification {

    void "test contents length constraint"() {
    	when: "we have a record text"
    	Record rec = new Record() 
    	RecordText rText = new RecordText(record:rec, contents:"hi")

    	then: 
    	rText.validate() == true 

    	when: "we add a too-short contents"
    	rText.contents = ""

    	then:
    	rText.validate() == false 

    	when: "we add a too-long contents"
    	rText.contents = '''
			Far far away, behind the word mountains, far from the countries Vokalia and
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove
			right at the coast of the Semantics, a large language ocean. A small river 
			named Duden flows by their place and supplies it with the necessary regelialia. 
			It is a paradisemati
		'''

    	then: 
    	rText.validate() == false 

    	when: "we add a valid contents again"
    	rText.contents = "hi"

    	then: 
    	rText.validate() == true 

    }

    @Ignore
    void "test cancelling scheduled texts"() {
    }

    void "test defining a sendAt time sets futureText as needed"() {
    	when: "we have a record text"
    	Record rec = new Record() 
    	RecordText rText = new RecordText(record:rec, contents:"hi")
    	assert rText.validate() 

    	then: 
    	rText.futureText == false 
    	rText.sendAt == null 

    	when: "we set a sendAt time in the future"
    	rText.sendAt = DateTime.now().plusDays(1)

    	then: 
    	rText.futureText == true 
    	rText.sendAt.isAfterNow()

    	when: "we set a sendAt time in the past"
    	rText.sendAt = DateTime.now().minusDays(1)

    	then: 
    	rText.futureText == false 
    	rText.sendAt.isBeforeNow()
    }
}
