package org.textup

import grails.test.spock.IntegrationSpec
import grails.validation.ValidationErrors
import spock.lang.Shared
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class PhoneIntegrationSpec extends IntegrationSpec {

	TextService mockTextService = [text:{ Phone fromPhone, Contactable toContact, 
        RecordText text -> 
        List<ContactNumber> nums = toContact.numbers
        if (nums && !nums.isEmpty()) {
            RecordItemReceipt receipt = new RecordItemReceipt(apiId:"test")
            receipt.receivedByAsString = nums[0]
            text.addToReceipts(receipt)
        }
        new Result(success:true, payload:text) 
    }] as TextService

    @Shared
    def grailsApplication

    def setup() {
    	Contact.metaClass.constructor = { Map m ->
    		def instance = new Contact() 
    		instance.properties = m
    		instance.textService = mockTextService
    		instance
    	}
    	//Creating a new contact in Phone passes in an empty map into 
        // the constructor so uses the map constructor so we bypass
        // the problem of needing to override the empty constructor
        // and running on NPE on the getBeam call
    }

    void "test operations on contacts"() {
    	given: 
    	Phone p = new Phone()
    	p.numberAsString = "5223334449"
    	p.save(flush:true, failOnError:true)

    	when: "we create a contact with no numbers"
    	Result res = p.createContact()

    	then: 
    	res.success == true 

    	when: "we create a contact with multiple valid numbers"
        Contact c1 = res.payload
    	res.payload.save(flush:true, failOnError:true)
    	res = p.createContact([:], ["123 435 4903", "290 329 4309"])
    	assert res.success 
        Contact c2 = res.payload 
    	res.payload.save(flush:true, failOnError:true)

    	then: 
    	res.payload.numbers.size() == 2

        when: "we create a few more contacts and then list them"
        Contact c3 = p.createContact().payload, c4 = p.createContact().payload, 
            c5 = p.createContact().payload, c6 = p.createContact().payload
        [c3, c4, c5, c6].eachWithIndex { Contact c, int i ->
            c.lastRecordActivity = DateTime.now(DateTimeZone.UTC).plusMinutes(i)
            c.save(flush:true, failOnError:true)
        }

        then:
        p.contacts == [c6, c5, c4, c3, c2, c1]

        when: "we mark some contacts are unread"
        c2.status = Constants.CONTACT_UNREAD
        c5.status = Constants.CONTACT_UNREAD
        [c2, c5]*.save(flush:true, failOnError:true)

        then: 
        p.contacts == [c5, c2, c6, c4, c3, c1]

        /*
        For some reason, if the below block is before any other, we get a
        NullPointerException on flushing during save
         */
        when: "we create a contact with multiple numbers, valid and invalid"
        res = p.createContact([:], ["123 435 4903", "ds123invalid", "290 329 4309"])

        then: 
        res.success == false 
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1
    }

    void "test deletion with numbers"() {
    	given: "a phone"
    	Phone p = new Phone()
    	p.numberAsString = "5223334444"
    	p.save(flush:true)

    	when: "we add associated relationships then delete"
		ContactTag t1 = new ContactTag(phone:p, name:"tag1"), 
    		t2 = new ContactTag(phone:p, name:"tag2")
    	assert t1.save(flush:true) && t2.save(flush:true)
    	Contact c1 = new Contact(phone:p), c2 = new Contact(phone:p)
    	assert c1.save(flush:true) && c2.save(flush:true)
    	assert (new TagMembership(tag:t1, contact:c1)).save(flush:true)
    	assert (new TagMembership(tag:t2, contact:c1)).save(flush:true)

    	assert c1.mergeNumber("222 333 4448").success
    	assert c2.mergeNumber("222 333 4448").success
    	assert c2.mergeNumber("222 333 4449").success
    	assert c1.save(flush:true) && c2.save(flush:true)

    	int tBaseline = ContactTag.count(), cBaseline = Contact.count(), 
    		mBaseline = TagMembership.count(), pBaseline = Phone.count(), 
    		rBaseline = Record.count(), nBaseline = ContactNumber.count()

	    p.delete(flush:true)

    	then: 
    	ContactTag.count() == tBaseline - 2
		Contact.count() == cBaseline - 2
		TagMembership.count() == mBaseline - 2
		Phone.count() == pBaseline - 1
		Record.count() == rBaseline - 2
		ContactNumber.count() == nBaseline - 3
    }

    void "test sending a mass text"() {
    	given: 
    	Phone p = new Phone()
    	p.numberAsString = "5223334445"
    	assert p.save(flush:true)
        p.authService = [parseContactIdsByPermission:{ List<Long> contactIds ->
                new ParsedResult<Long,Long>(valid:contactIds)
            },
            parseTagIdsByPermission:{ List<Long> tagIds ->
                new ParsedResult<Long,Long>(valid:tagIds)
            }
        ] as AuthService

    	ContactTag t1 = new ContactTag(phone:p, name:"tag1"), 
    		t2 = new ContactTag(phone:p, name:"tag2"), 
    		t3 = new ContactTag(phone:p, name:"tag3")
    	assert t1.save(flush:true) && t2.save(flush:true) &&
    		t3.save(flush:true)
    	Contact c1 = new Contact(phone:p), c2 = new Contact(phone:p), 
    		c3 = new Contact(phone:p), c4 = new Contact(phone:p)
    	assert c1.save(flush:true) && c2.save(flush:true) &&
    		c3.save(flush:true) && c4.save(flush:true)
    	assert (new TagMembership(tag:t1, contact:c1)).save(flush:true)
    	assert (new TagMembership(tag:t2, contact:c1)).save(flush:true)
    	assert (new TagMembership(tag:t2, contact:c2)).save(flush:true)
    	assert (new TagMembership(tag:t3, contact:c2)).save(flush:true)

    	String numForMultipleContacts = "222 333 4448", 
    		uniqueValidNumber = "222 333 4411", 
    		uniqueInvalidNumber = "123invalid"
    	Result setupRes1 = c1.mergeNumber(numForMultipleContacts), 
    		setupRes2 = c2.mergeNumber(numForMultipleContacts),
    		setupRes3 = c2.mergeNumber("222 333 4449"),
    		setupRes4 = c3.mergeNumber("222 333 4410")
    	assert setupRes1.success && setupRes2.success && 
    		setupRes3.success && setupRes4.success
    	ContactNumber c1n1 = setupRes1.payload, 
    		c2n1 = setupRes2.payload, 
    		c2n2 = setupRes3.payload, 
    		c3n1 = setupRes4.payload
    	assert c1.save(flush:true) && c2.save(flush:true) &&
    		c3.save(flush:true)

    	when: "we text a message that is too long"
    	String tooLongMsg = '''
			Far far away, behind the word mountains, far from the countries Vokalia and
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove
			right at the coast of the Semantics, a large language ocean. A small river 
			named Duden flows by their place and supplies it with the necessary regelialia. 
			It is a paradisemati
		'''
		Result res = p.text(tooLongMsg, [], [c1, c2]*.id, [])

		then:
		res.success == false 
		res.payload instanceof Map 
		res.payload.code == "textService.error.messageLength"

    	when: "we text contacts"
        int rBaseline = RecordItemReceipt.count(), 
            tBaseline = RecordText.count()
		res = p.text("hi", [], [c1, c2, c4]*.id, [])
		p.save(flush:true, failOnError:true)

    	then: 
    	res.success == true 
    	res.payload.invalidNumbers.isEmpty()
    	res.payload.invalidOrForbiddenContactableIds.isEmpty()
        res.payload.invalidOrForbiddenTagIds.isEmpty()
    	res.payload.newItems.size() == 3 //c4 still has a RecordText
        //c1, c2, c4
        RecordText.count() == tBaseline + 3
        //1 for c1, 1 for c2, 0 for c4
        RecordItemReceipt.count() == rBaseline + 2

    	when: "we text overlapping contacts and tags"
        rBaseline = RecordItemReceipt.count()
        tBaseline = RecordText.count()
		res = p.text("hi", [], [c2, c3, c4]*.id, [t1, t2]*.id)
		p.save(flush:true, failOnError:true)

    	then: 
    	res.success == true 
    	res.payload.invalidNumbers.isEmpty()
        res.payload.invalidOrForbiddenContactableIds.isEmpty()
        res.payload.invalidOrForbiddenTagIds.isEmpty()
    	//c1 added from inclusion in tags 1 and 2
    	res.payload.newItems.size() == 4
        //c1, c2, c3, c4
        RecordText.count() == tBaseline + 4
        //1 for c1, 1 for c2, 1 for c3, 0 for c4
        RecordItemReceipt.count() == rBaseline + 3

    	when: "we text contact, tags, with all valid phone numbers"
        rBaseline = RecordItemReceipt.count()
        tBaseline = RecordText.count()
    	res = p.text("hi", [numForMultipleContacts, uniqueValidNumber],
    		[c3, c4]*.id, [t3]*.id)
    	p.save(flush:true, failOnError:true)

    	then: 
    	res.success == true 
    	res.payload.invalidNumbers.isEmpty()
    	res.payload.invalidOrForbiddenContactableIds.isEmpty()
        res.payload.invalidOrForbiddenTagIds.isEmpty()
    	//c1 and c2 found from inclusion in numForMultipleContacts
    	//new contact created for uniqueValidNumber + c3 + c4
    	res.payload.newItems.size() == 5
        //c1, c2, c3, c4, new contact
        RecordText.count() == tBaseline + 5
        //1 for c1, 1 for c2, 1 for c3, 0 for c4, 1 for new contact
        RecordItemReceipt.count() == rBaseline + 4

    	when: "we text contact, tags, with valid and invalid phone numbers"
        rBaseline = RecordItemReceipt.count()
        tBaseline = RecordText.count()
    	res = p.text("hi", [uniqueInvalidNumber], [c2]*.id, [t1, t2, t3]*.id)
    	p.save(flush:true, failOnError:true)

    	then:
    	res.success == true 
    	res.payload.invalidNumbers.size() == 1
    	res.payload.invalidNumbers[0] == (new PhoneNumber(number:uniqueInvalidNumber)).number
    	res.payload.invalidOrForbiddenContactableIds.isEmpty()
        res.payload.invalidOrForbiddenTagIds.isEmpty()
    	res.payload.newItems.size() == 2 //c1, c2
        //c1, c2
        RecordText.count() == tBaseline + 2
        //1 for c1, 1 for c2
        RecordItemReceipt.count() == rBaseline + 2
    }
}
