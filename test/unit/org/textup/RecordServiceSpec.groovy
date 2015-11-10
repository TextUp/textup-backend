package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import spock.lang.Shared
import spock.lang.Specification
import grails.plugin.springsecurity.SpringSecurityService
import org.textup.util.CustomSpec
import org.joda.time.DateTime
import static org.springframework.http.HttpStatus.*

@TestFor(RecordService)
@Domain([TagMembership, Contact, Phone, ContactTag,
	ContactNumber, Record, RecordItem, RecordNote, RecordText,
	RecordCall, RecordItemReceipt, PhoneNumber, SharedContact,
	TeamMembership, StaffPhone, Staff, Team, Organization,
	Schedule, Location, TeamPhone, WeeklySchedule, TeamContactTag])
@TestMixin(HibernateTestMixin)
class RecordServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
        lockService(LockService) { bean -> bean.autoWire = true }
    }
    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.lockService = getBean("lockService")
        [Phone, StaffPhone, TeamPhone].each { clazz ->
            clazz.metaClass.text = { String message, List<String> numbers,
                List<Long> contactableIds, List<Long> tagIds ->
                new Result(payload:new RecordResult())
            }
            clazz.metaClass.scheduleText = {String message, DateTime sendAt,
                List<String> numbers, List<Long> contactableIds, List<Long> tagIds ->
                new Result(payload:new RecordResult())
            }
            clazz.metaClass.call = { String number ->
                new Result(payload:new RecordResult())
            }
            clazz.metaClass.call = { Long contactableId ->
                new Result(payload:new RecordResult())
            }
        }
    }
    def cleanup() {
        super.cleanupData()
    }

    ///////////
    // Calls //
    ///////////

    void "test call status"() {
        // when: "update status with nonexistent apiId"

        // then:

        // when: "update with invalid status"

        // then:

        // when: "update valid with duration"

        // then:
        expect:
        1 ==2
    }

    void "test incoming call with multiple contacts"() {
        // when: "invalid from number"

        // then:

        // when: "incoming to multiple contacts"

        // then:
        expect:
        1 ==2
    }

    void "test incoming call and create new contact"() {
        // when: "invalid from number"

        // then:

        // when: "incoming and create new contact"

        // then:
        expect:
        1 ==2
    }

    void "test outgoing call with multiple contacts"() {
        // when: "invalid to number"

        // then:

        // when: "outgoing to multiple contacts"

        // then:
        expect:
        1 ==2
    }

    void "test outgoing call and create new contact"() {
        // when: "invalid to number"

        // then:

        // when: "outgoing and create new contact"

        // then:
        expect:
        1 ==2
    }

    void "test creating outgoing RecordCall for a specific contact"() {
        // when: "no phone found for 'from' number"

        // then:

        // when: "invalid 'to' number"

        // then:

        // when: "contactId specified is not owned by 'from' phone"

        // then:

        // when: "valid creation of outgoing call for specified contactId"

        // then:
        expect:
        1 ==2
    }

    void "test updating timestamps"() {
        // when: "create call for incoming for multiple contacts"

        // then: "all timestamps updated"

        // when: "create call for outgoing for multiple contacts"

        // then: "all timestamps updated"

        // when: "create outgoing for one specific contact"

        // then: "all timestamps updated"
        expect:
        1 ==2
    }

    ////////////
    // Create //
    ////////////

    void "test create errors"() {
        when: "we create record for a nonexistent team"
        Result res = service.create(Team, -88L, [:])

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.create.noPhone"
        res.payload.status == UNPROCESSABLE_ENTITY

        when: "we pass in a body that cannot be inferred to be one of the 3 record item types"
        res = service.create(Team, t1.id, [:])

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.create.unknownType"
        res.payload.status == UNPROCESSABLE_ENTITY
    }

    void "create text"() {
        when: "we specify a valid text without specifying where to send it"
        Map itemInfo = [contents:"hi"]
        Result res = service.create(Team, t1.id, itemInfo)

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.create.noTextRecipients"
        res.payload.status == BAD_REQUEST

        when: "we specify a valid text where some of the ids are not numbers"
        itemInfo = [contents:"hi", sendToContacts:[tC1.id, "notANumber"]]
        res = service.create(Team, t1.id, itemInfo)

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.create.idIsNotLong"
        res.payload.status == BAD_REQUEST

        when: "we specify a valid id with valid recipients"
        itemInfo = [contents:"hi", sendToPhoneNumbers:["2223334444"], sendToContacts:[tC1.id]]
        res = service.create(Team, t1.id, itemInfo)

        then:
        res.success == true
        res.payload instanceof RecordResult
    }

    void "create call"() {
        when: "we specify a valid call specifying both recipient type"
        Map itemInfo = [callPhoneNumber:"12223334444", callContact:tC1.id]
        Result res = service.create(Team, t1.id, itemInfo)

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.create.canCallOnlyOne"
        res.payload.status == BAD_REQUEST

        when: "we specify a valid call and specify a recipient"
        itemInfo = [callContact:tC1.id]
        res = service.create(Team, t1.id, itemInfo)

        then:
        res.success == true
        res.payload instanceof RecordResult
    }

    void "create note"() {
        given:
        service.authService = [hasPermissionsForContact:{ Long cId -> true },
            getSharedContactForContact:{ Long cId -> true }] as AuthService

        when: "we try to add to a nonexistent contact"
        Map itemInfo = [note:"note", addToContact:-88L]
        Result res = service.create(Team, t1.id, itemInfo)

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.create.contactNotFound"
        res.payload.status == NOT_FOUND

        when: "we try to add to a contact that is found"
        itemInfo = [note:"note", addToContact:tC1.id]
        res = service.create(Team, t1.id, itemInfo)

        then:
        res.success == true
        res.payload instanceof RecordResult
    }

    ////////////
    // Update //
    ////////////

    void "test update errors"() {
        given:
        service.authService = [getLoggedIn:{ s1 }] as AuthService
        RecordCall rCall1 = c1.record.addCall([durationInSeconds:888], null).payload
        rCall1.save(flush:true, failOnError:true)

        when: "we try to update a nonexistent item"
        Map updateInfo = [:]
        Result res = service.update(-88L, updateInfo)

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.update.itemNotFound"
        res.payload.status == NOT_FOUND

        when: "we try to update a RecordCall"
        updateInfo = [durationInSeconds:8]
        res = service.update(rCall1.id, updateInfo)

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.update.notEditable"
        res.payload.status == FORBIDDEN
    }

    void "test update note"() {
        given:
        service.authService = [getLoggedIn:{ s1 }] as AuthService
        RecordNote rNote1 = c1.record.addNote([note:"note", editable:false], null).payload
        RecordNote rNote2 = c1.record.addNote([note:"note", editable:true], null).payload
        [rNote1, rNote2]*.save(flush:true, failOnError:true)

        when: "we try to update a noneditable note"
        String updatedNote = "updated"
        Map updateInfo = [note:updatedNote]
        Result res = service.update(rNote1.id, updateInfo)

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.update.notEditable"
        res.payload.status == FORBIDDEN

        when: "we try to update an editable note"
        updateInfo = [note:updatedNote]
        res = service.update(rNote2.id, updateInfo)

        then:
        res.success == true
        res.payload instanceof RecordNote
        res.payload.id == rNote2.id
        res.payload.note == updatedNote
    }

    void "test update text"() {
    	given:
        service.authService = [getLoggedIn:{ s1 }] as AuthService

        when: "we try to update the contents of a text"
        String updatedContents = "updated"
        DateTime sendAt = DateTime.now().plusHours(2)
        Map updateInfo = [contents:updatedContents, sendAt:sendAt]
        Result res = service.update(rText1.id, updateInfo)

        then:
        res.success == false
        res.type == Constants.RESULT_MESSAGE_STATUS
        res.payload.code == "recordService.update.notEditable"
        res.payload.status == FORBIDDEN

        when: "we try to update updateable fields of a text"
        updateInfo = [sendAt:sendAt]
        res = service.update(rText1.id, updateInfo)

        then:
        res.success == true
        res.payload instanceof RecordText
        res.payload.id == rText1.id
        res.payload.contents == rText1.contents
        res.payload.futureText == true
        res.payload.sendAt == sendAt

        when: "we cancel future text"
        updateInfo = [futureText:false]
        res = service.update(rText1.id, updateInfo)

        then:
        res.success == true
        res.payload instanceof RecordText
        res.payload.id == rText1.id
        res.payload.contents == rText1.contents
        res.payload.futureText == false
        res.payload.sendAt == sendAt
    }
}
