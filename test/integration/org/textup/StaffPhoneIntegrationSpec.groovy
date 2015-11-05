package org.textup

import grails.test.spock.IntegrationSpec
import grails.validation.ValidationErrors
import spock.lang.Ignore
import spock.lang.Shared
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class StaffPhoneIntegrationSpec extends IntegrationSpec {

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

    Staff s1, s2

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
        
        //set up two staff members on the same team 
        Organization org = new Organization(name:"11-org")
        org.location = new Location(address:"Testing Address", lat:0G, lon:0G)
        org.save(flush:true)

        Team t1 = new Team(name:"Team1", org:org)
        t1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
        t1.save(flush:true, failOnError:true)

        s1 = new Staff(username:"10staff", password:"password", 
            name:"Staff", email:"staff@textup.org", org:org)
        s2 = new Staff(username:"11staff", password:"password", 
            name:"Staff", email:"staff@textup.org", org:org)
        s1.personalPhoneNumberAsString = "111 222 3333"
        s2.personalPhoneNumberAsString = "111 222 3333"
        s1.save(flush:true, failOnError:true)
        s2.save(flush:true, failOnError:true)

        (new TeamMembership(staff:s1, team:t1)).save(flush:true, failOnError:true)
        (new TeamMembership(staff:s2, team:t1)).save(flush:true, failOnError:true)
    }

	@Ignore
    void "test calling including shared contacts"() {
    }

    void "test texting including shared contacts"() {
        given: 
    	StaffPhone p = new StaffPhone()
    	p.numberAsString = "9223334445"
        p.ownerId = s1.id 
    	p.save(flush:true, failOnError:true)
        StaffPhone p2 = new StaffPhone()
        p2.numberAsString = "9223334446"
        p2.ownerId = s2.id
        p2.save(flush:true, failOnError:true)

        HashSet<Long> notMyContactIds = new HashSet<>()
        Map<Long,Long> contactIdToSharedContact = [:]
        p.authService = [findInvalidOrForbiddenContactIds:{ List<Long> contactIds ->
                ParsedResult parsed= new ParsedResult()
                contactIds.each { Long cId ->
                    if (notMyContactIds.contains(cId)) {
                        parsed.invalid << cId
                    }
                    else {
                        parsed.valid << cId
                    }
                }
                parsed
            },
            findInvalidOrForbiddenTagIds:{ List<Long> tagIds ->
                new ParsedResult<Long,Long>(valid:tagIds)
            }, 
            findUnsharedContactIds:{ List<Long> contactIds ->
                ParsedResult parsed= new ParsedResult()
                contactIds.each { Long cId ->
                    if (contactIdToSharedContact.containsKey(cId)) {
                        parsed.valid << contactIdToSharedContact.get(cId)
                    }
                    else {
                        parsed.invalid << cId
                    }
                }
                parsed
            }
        ] as AuthService
        //Add tags
    	ContactTag t1 = new ContactTag(phone:p, name:"tag1"), 
    		t2 = new ContactTag(phone:p, name:"tag2"), 
    		t3 = new ContactTag(phone:p, name:"tag3")
    	assert t1.save(flush:true) && t2.save(flush:true) &&
    		t3.save(flush:true)
        //Add contacts
    	Contact c1 = new Contact(phone:p), c2 = new Contact(phone:p), 
    		c3 = new Contact(phone:p), c4 = new Contact(phone:p)
    	assert c1.save(flush:true) && c2.save(flush:true) &&
    		c3.save(flush:true) && c4.save(flush:true)
        //Add contacts to tags
    	assert (new TagMembership(tag:t1, contact:c1)).save(flush:true)
    	assert (new TagMembership(tag:t2, contact:c1)).save(flush:true)
    	assert (new TagMembership(tag:t2, contact:c2)).save(flush:true)
    	assert (new TagMembership(tag:t3, contact:c2)).save(flush:true)
        //Add shared contact
        Contact other1 = new Contact(phone:p2).save(flush:true, failOnError:true)
        Result<SharedContact> shareRes = p2.shareContact(other1, p, Constants.SHARED_DELEGATE)
        assert shareRes.success 
        p2.save(flush:true, failOnError:true)
        notMyContactIds << other1.id
        contactIdToSharedContact.put(other1.id, shareRes.payload)
        //Add phone numbers 
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

        when: "we text contact, tags, where one of the contact is SHARED WITH US"
        rBaseline = RecordItemReceipt.count()
        tBaseline = RecordText.count()
        res = p.text("hi", [numForMultipleContacts], [c3, c4, other1]*.id, [t3]*.id)
        p.save(flush:true, failOnError:true)

        then:
        res.success == true 
        res.payload.invalidNumbers.isEmpty()
        res.payload.invalidOrForbiddenContactableIds.isEmpty()
        res.payload.invalidOrForbiddenTagIds.isEmpty()
        //c1 and c2 found from inclusion in numForMultipleContacts
        //shared contact + c3 + c4
        res.payload.newItems.size() == 5
        //c1, c2, c3, c4, shared contact
        RecordText.count() == tBaseline + 5
        //1 for c1, 1 for c2, 1 for c3, 0 for c4, 0 for shared contact
        RecordItemReceipt.count() == rBaseline + 3

        when: "shared contact stops being shared, we can no longer text"
        //stop sharing 
        p2.stopSharingWith(p)
        p2.save(flush:true, failOnError:true)
        contactIdToSharedContact.remove(other1.id)
        //try texting
        rBaseline = RecordItemReceipt.count()
        tBaseline = RecordText.count()
        res = p.text("hi", [numForMultipleContacts], [c3, c4, other1]*.id, [t3]*.id)
        p.save(flush:true, failOnError:true)

        then:
        res.success == true 
        res.payload.invalidNumbers.isEmpty()
        res.payload.invalidOrForbiddenContactableIds.size() == 1
        res.payload.invalidOrForbiddenContactableIds[0] == other1.id
        res.payload.invalidOrForbiddenTagIds.isEmpty()
        //c1 and c2 found from inclusion in numForMultipleContacts + c3 + c4
        res.payload.newItems.size() == 4
        //c1, c2, c3, c4
        RecordText.count() == tBaseline + 4
        //1 for c1, 1 for c2, 1 for c3, 0 for c4
        RecordItemReceipt.count() == rBaseline + 3
    }
}
