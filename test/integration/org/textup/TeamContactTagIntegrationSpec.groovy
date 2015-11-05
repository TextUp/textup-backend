package org.textup

import grails.test.spock.IntegrationSpec
import grails.validation.ValidationErrors
import spock.lang.Shared

class TeamContactTagIntegrationSpec extends IntegrationSpec {

	TextService mockTextService = [text:{ Phone fromPhone, Contactable toContact, 
        RecordText text -> 
        List<ContactNumber> nums = toContact.numbers
        if (!nums?.isEmpty()) {
        	RecordItemReceipt receipt = new RecordItemReceipt(apiId:"test")
			receipt.receivedByAsString = nums[0]
			text.addToReceipts(receipt)
        }
        new Result(success:true, payload:text) 
    }] as TextService

    private String _loggedInName = "Testing"
    AuthService mockAuthService = [getLoggedIn:{ 
        [name:_loggedInName] as Staff 
    }] as AuthService

    @Shared
    def grailsApplication

    def setup() {
    	Contact.metaClass.constructor = { Map m ->
            def instance = new Contact() 
            instance.properties = m
            instance.textService = mockTextService
            instance
        }
        TeamContactTag.metaClass.constructor = { ->
            def instance = grailsApplication.mainContext.getBean(TeamContactTag.name)
            instance.authService = mockAuthService
            instance
        }
        TeamContactTag.metaClass.constructor = { Map m ->
            def instance = new TeamContactTag() 
            instance.properties = m
            instance.authService = mockAuthService
            instance
        }
    }

    void "test texting"() {
        given: 
        Phone p = new Phone()
        p.numberAsString = "7223334446"
        p.save(flush:true, failOnError:true)
        TeamContactTag t = new TeamContactTag(name:"tag1", phone:p)
        t.save(flush:true, failOnError:true)

        Contact c1 = new Contact(phone:p), c2 = new Contact(phone:p), 
            c3 = new Contact(phone:p), c4 = new Contact(phone:p)
        assert c1.save(flush:true) && c2.save(flush:true) &&
            c3.save(flush:true) && c4.save(flush:true)

        assert (new TagMembership(tag:t, contact:c1)).save(flush:true)
        assert (new TagMembership(tag:t, contact:c2, hasUnsubscribed:true)).save(flush:true)
        assert (new TagMembership(tag:t, contact:c3, hasUnsubscribed:true)).save(flush:true)
        assert (new TagMembership(tag:t, contact:c4)).save(flush:true)

        assert c1.mergeNumber("222 333 8888").success
        assert c1.mergeNumber("222 333 8889").success
        assert c4.mergeNumber("222 333 8888").success

        when: "try to text a too-long message"
        String tooLongMsg = '''
			Far far away, behind the word mountains, far from the countries Vokalia and
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove
			right at the coast of the Semantics, a large language ocean. A small river 
			named Duden flows by their place and supplies it with the necessary regelialia. 
			It is a paradisemati
		'''
		Result res = t.text(contents:tooLongMsg)

        then: 
        res.success == false 
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1

        when: "try to text a valid message"
        int tBaseline = RecordText.count(), 
        	rBaseline = RecordItemReceipt.count()
        res = t.text(contents:"hi")
        t.save(flush:true)

        then: 
        res.success == true 
        RecordText.count() == tBaseline + 2 + 1 //2 subs and 1 for tag
        RecordText.findAllByRecord(t.record).every { it.authorName == _loggedInName }
        RecordText.findAllByRecord(c1.record).every { it.authorName == _loggedInName }
        RecordText.findAllByRecord(c4.record).every { it.authorName == _loggedInName }
        RecordItemReceipt.count() == rBaseline + (2 * 2) //2 subs, doubled in tag
    }
}
