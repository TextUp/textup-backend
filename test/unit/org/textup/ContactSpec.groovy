package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.springframework.context.MessageSource
import org.textup.types.AuthorType
import org.textup.util.CustomSpec
import org.textup.validator.Author
import org.textup.validator.IncomingText
import org.textup.validator.TempRecordReceipt
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class ContactSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    void "test constraints"() {
    	when: "we have a contact with only a phone defined"
    	Contact c1 = new Contact(phone:p1)
    	c1.resultFactory = getResultFactory()

    	then: "an empty record is automatically added"
    	c1.validate() == true
    	c1.record != null

    	when: "we add a tag membership and define a too-long note"
    	c1.save(flush:true, failOnError:true)
    	c1.note = '''
			Far far away, behind the word mountains, far from the countries Vokalia and
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove
			right at the coast of the Semantics, a large language ocean. A small river named
			Duden flows by their place and supplies it with the necessary regelialia. It is a
			paradisematic country, in which roasted parts of sentences fly into your mouth.
			Even the all-powerful Pointing has no control about the blind texts it is an almost
			unorthographic life One day however a small line of blind text by the name of Lorem
			Ipsum decided to leave for the far World of Grammar. The Big Oxmox advised her not
			to do so, because there were thousands of bad Commas, wild Question Marks and
			devious Semikoli, but the Little Blind Text didn’t listen. She packed her seven
			versalia, put her initial into the belt and made herself on the way. When she
			reached the first hills of the Italic Mountains, she had a last view back on the
			skyline of her hometown Bookmarksgrove, the headline of Alphabet Village and the
			subline of her own road, the Line Lane. Pityful a ret
    	'''

    	then:
    	c1.validate() == false
    	c1.errors.errorCount == 1

    	when: "we remove the note"
    	c1.note = null

    	then:
    	c1.validate() == true
    }

 	void "test no duplicate numbers for one contact, autoincrement preference"() {
 		given:
 		int maxPref = c1.numbers.max { it.preference }.preference
 		int numNums = c1.numbers.size()
 		int numBaseline = ContactNumber.count()
 		String number = "123 434 9230"

    	when: "we try to add a unique number"
    	ContactNumber cn = c1.mergeNumber(number).payload
    	cn.save(flush:true, failOnError:true)

    	then:
    	c1.numbers.size() == numNums + 1
    	cn.preference == maxPref + 1
    	ContactNumber.count() == numBaseline + 1

    	when: "we try to add a duplicate number"
    	cn = c1.mergeNumber(number).payload
    	cn.save(flush:true, failOnError:true)

    	then:
    	c1.numbers.size() == numNums + 1
    	cn.preference == maxPref + 1
    	ContactNumber.count() == numBaseline + 1

    	when: "we try to add another unique number"
    	cn = c1.mergeNumber("123 439 0980").payload
    	cn.save(flush:true, failOnError:true)

    	then:
    	c1.numbers.size() == numNums + 2
    	cn.preference == maxPref + 2
    	ContactNumber.count() == numBaseline + 2

		when: "we try to add another unique number"
		cn = c1.mergeNumber("123 439 0981").payload
    	cn.save(flush:true, failOnError:true)

    	then:
    	c1.numbers.size() == numNums + 3
    	cn.preference == maxPref + 3
    	ContactNumber.count() == numBaseline + 3

    	when: "we delete a number"
    	c1.deleteNumber(number)
    	c1.save(flush:true, failOnError:true)

    	then:
    	c1.numbers.size() == numNums + 2
    	ContactNumber.count() == numBaseline + 2
    }

    void "test store record items"() {
    	given:
    	int textBaseline = RecordText.count()
    	int callBaseline = RecordCall.count()
    	int receiptBaseline = RecordItemReceipt.count()

    	when: "we store outgoing text"
    	TempRecordReceipt receipt = new TempRecordReceipt(apiId:"testing",
    		receivedByAsString:"1112223333")
    	RecordText outText = c1.storeOutgoingText("hello", receipt, s1).payload
    	c1.save(flush:true, failOnError:true)

    	then:
    	RecordText.count() == textBaseline  + 1
    	RecordItemReceipt.count() == receiptBaseline  + 1
    	outText.authorType == AuthorType.STAFF
    	outText.authorId == s1.id
    	outText.authorName == s1.name
    	outText.receipts.any { it.receivedByAsString == receipt.receivedByAsString }
    	outText.outgoing == true

    	when: "store outgoing call"
    	receipt = new TempRecordReceipt(apiId:"testing",
    		receivedByAsString:"1112223333")
    	RecordCall outCall = c1.storeOutgoingCall(receipt, s1).payload
    	c1.save(flush:true, failOnError:true)

    	then:
    	RecordCall.count() == callBaseline  + 1
    	RecordItemReceipt.count() == receiptBaseline  + 2
    	outCall.authorType == AuthorType.STAFF
    	outCall.authorId == s1.id
    	outCall.authorName == s1.name
    	outText.receipts.any { it.receivedByAsString == receipt.receivedByAsString }
    	outCall.outgoing == true

    	when: "store incoming text"
    	IncomingText textValidator = new IncomingText(apiId:"testing", message:"hello")
    	IncomingSession session = new IncomingSession(phone:p1, numberAsString:"1112223333")
    	RecordText inText = c1.storeIncomingText(textValidator, session).payload
    	c1.save(flush:true, failOnError:true)

    	then:
    	RecordText.count() == textBaseline  + 2
    	RecordItemReceipt.count() == receiptBaseline  + 3
    	inText.authorType == AuthorType.SESSION
    	inText.authorId == session.id
    	inText.authorName == session.numberAsString
    	inText.outgoing == false

    	when: "store incoming call"
    	RecordCall inCall = c1.storeIncomingCall("apiId", session).payload
    	c1.save(flush:true, failOnError:true)

    	then:
    	RecordCall.count() == callBaseline  + 2
    	RecordItemReceipt.count() == receiptBaseline  + 4
    	inCall.authorType == AuthorType.SESSION
    	inCall.authorId == session.id
    	inCall.authorName == session.numberAsString
    	inCall.outgoing == false
    }
}
