package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.test.runtime.FreshRuntime;

@FreshRuntime
@Domain([TagMembership, Contact, Phone, ContactTag, 
	ContactNumber, Record, RecordItem, RecordNote, RecordText, 
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact, 
	TeamMembership, StaffPhone, Staff, Team, Organization, 
	Schedule, Location, TeamPhone, TeamContactTag])
@TestMixin(HibernateTestMixin)
@Unroll
class TeamContactTagSpec extends Specification {

    private String _loggedInName = "Testing"

    static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        ResultFactory fac = grailsApplication.mainContext.getBean("resultFactory")
        fac.messageSource = [getMessage:{ String code, 
            Object[] parameters, Locale locale -> code }] as MessageSource

        TeamContactTag.metaClass.constructor = { Map m ->
            def instance = new TeamContactTag() 
            instance.properties = m
            instance.authService = [getLoggedIn:{ 
                [name:_loggedInName] as Staff 
            }] as AuthService
            instance.resultFactory = getResultFactory()
            instance
        }
        ///////////////////////////////////////////////////////////
        // Haven't figured out how to inject resultFactory into  //
        // Record no-arg controller so we are manually override  //
        // each time we instantiate a record                     //
        ///////////////////////////////////////////////////////////
    }
    private ResultFactory getResultFactory() {
        grailsApplication.mainContext.getBean("resultFactory")
    }

    void "test constraints and deletion"() {
        given: 
        Phone p = new Phone()
        p.numberAsString = "7223334444"
        p.save(flush:true, failOnError:true)
        Contact c = new Contact(phone:p)
        c.save(flush:true, failOnError:true)

        when: "we add a tag with a unique name"
        String tName = "tag1"
        TeamContactTag t = new TeamContactTag(name:tName, phone:p)

        then:
        t.validate() == true 

        when: "we add a tag with a duplicate name"
        t.save(flush:true)
        t = new TeamContactTag(name:tName, phone:p)

        then:
        t.validate() == false 
        t.errors.errorCount == 1

        when: "we change to a unique name"
        t.name = "tag2"

        then: 
        t.validate() == true 

        when: "we delete"
        t.save(flush:true)
        assert (new TagMembership(tag:t, contact:c)).save(flush:true, failOnError:true)
        int tBaseline = TeamContactTag.count(), 
            mBaseline = TagMembership.count(), 
            rBaseline = Record.count()
        t.delete(flush:true)

        then:
        TeamContactTag.count() == tBaseline - 1
        TagMembership.count() == mBaseline - 1
        Record.count() == rBaseline - 1
    }

    @Ignore
    void "test scheduled texts"() {
    }

    void "test unsupported operations"() {
        given: 
        Phone p = new Phone()
        p.numberAsString = "7223334445"
        p.save(flush:true, failOnError:true)
        TeamContactTag t = new TeamContactTag(name:"tag1", phone:p)
        t.save(flush:true, failOnError:true)

        when: "we try to call"
        Result res = t.call([:])

        then: 
        res.success == false 
        res.payload instanceof Map
        res.payload.code == "teamContactTag.error.notSupported"

        when: "we try to merge number"
        res = t.mergeNumber("", [:])

        then: 
        res.success == false 
        res.payload instanceof Map
        res.payload.code == "teamContactTag.error.notSupported"

        when: "we try to delete number"
        res = t.deleteNumber("")

        then:
        res.success == false 
        res.payload instanceof Map
        res.payload.code == "teamContactTag.error.notSupported"
    }

    void "test note operations include authorId"() {
        given: 
        Phone p = new Phone()
        p.numberAsString = "7223334446"
        p.save(flush:true, failOnError:true)
        TeamContactTag t = new TeamContactTag(name:"tag1", phone:p)
        Record rec = new Record()
        rec.resultFactory = getResultFactory()
        t.record = rec 
        rec.save()
        t.save(flush:true, failOnError:true)

        when: "add a note"
        Result res = t.addNote(note:"hi")

        then:
        res.success == true 
        res.payload instanceof RecordResult
        res.payload.newItems[0].instanceOf(RecordNote)
        res.payload.newItems[0].authorName == _loggedInName

        when: "we edit a note"
        RecordNote n1 = res.payload.newItems[0]
        n1.save(flush:true, failOnError:true)
        String newNote = "changed"
        res = t.editNote(n1.id, [note:newNote])

        then: 
        res.success == true 
        res.payload instanceof RecordResult
        res.payload.newItems[0].instanceOf(RecordNote)
        res.payload.newItems[0].authorName == _loggedInName
        res.payload.newItems[0].note == newNote
    }
}
