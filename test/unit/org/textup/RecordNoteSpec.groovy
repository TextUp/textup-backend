package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([Record, RecordItem, RecordNote, RecordText, RecordCall, 
	RecordItemReceipt, PhoneNumber])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordNoteSpec extends Specification {

    void "test note length constraint"() {
    	when: 
    	Record rec = new Record()
    	RecordNote rNote = new RecordNote(record:rec, note:"hi")

    	then:
    	rNote.validate() == true 
    	rNote.editable == true 

    	when: "we add a too-short note"
    	rNote.note = ""

    	then:
    	rNote.validate() == false 

    	when: "we add a too-long note"
    	rNote.note = '''
			Far far away, behind the word mountains, far from the countries Vokalia and
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove 
			right at the coast of the Semantics, a large language ocean. A small river named
			Duden flows by their place and supplies it with the necessary re
		'''

    	then: 
    	rNote.validate() == false 

    	when: "we add a valid note again"
    	rNote.note = "hi"

    	then: 
    	rNote.validate() == true 
    }

    void "test cannot edit note if editable is false"() {
    	when: "we have an uneditable note"
    	Record rec = new Record()
    	String origMsg = "hi"
    	RecordNote rNote = new RecordNote(record:rec, note:origMsg, editable:false)

    	then: "is valid"
    	rNote.validate() == true 
    	rNote.editable == false 

    	when: "we try to change the note"
    	rNote.note = "I am a new message."

    	then: "no change occurs"
    	rNote.note == origMsg
    }
}
