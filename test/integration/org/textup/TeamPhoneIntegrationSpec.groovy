package org.textup

import grails.test.spock.IntegrationSpec
import grails.validation.ValidationErrors
import spock.lang.Shared

class TeamPhoneIntegrationSpec extends IntegrationSpec {

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

    void "test texting with team contact tags"() {
        given: 
        TeamPhone p = new TeamPhone()
        p.numberAsString = "6223334444"
        assert p.save(flush:true)

        p.authService = [findInvalidOrForbiddenContactIds:{ List<Long> contactIds ->
                new ParsedResult<Long,Long>(valid:contactIds)
            },
            findInvalidOrForbiddenTagIds:{ List<Long> tagIds ->
                new ParsedResult<Long,Long>(valid:tagIds)
            }
        ] as AuthService

        TeamContactTag t1 = p.createTag(name:"tag1").payload, 
            t2 = p.createTag(name:"tag2").payload, 
            t3 = p.createTag(name:"tag3").payload
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
        Result res = p.text(tooLongMsg, [], [c1, c2], [])

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
        //c1, c2, c4, and no tag
        RecordText.count == tBaseline + 3
        //c1 has 1 number, c2 has 2 numbers, c4 has 0 numbers
        //but we only send at most once to each contact initially
        RecordItemReceipt.count() == rBaseline + 2 //no tag so don't double

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
        //c1, c2, c3, c4, t1, t2
        RecordText.count == tBaseline + 6
        //c1 has 1 number, c2 has 2 numbers, c3 has 1 number, c4 has 0 numbers
        //but we only send at most once to each contact initially
        RecordItemReceipt.count() == rBaseline + (3 * 2)

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
        //c1, c2, c3, c4, new contact, t3
        RecordText.count == tBaseline + 6
        //c1 has 1 number, c2 has 2 numbers, c3 has 1 number, 
        //c4 has 0 numbers and new contact has 1 number
        //but we only send at most once to each contact initially
        //t3 only contains c2
        RecordItemReceipt.count() == rBaseline + 3 + (1 * 2)

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
        //c1, c2, t1, t2, t3
        RecordText.count == tBaseline + 5
        //c1 has 1 number, c2 has 2 numbers
        //but we only send at most once to each contact initially
        //t1 contains c1, t2 contains c1 and c2, t3 contains c2
        RecordItemReceipt.count() == rBaseline + (1 * 3) + (1 * 3)
    }
}
