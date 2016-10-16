package org.textup

import com.amazonaws.HttpMethod
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.types.ReceiptStatus
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.http.HttpStatus.*

@TestFor(RecordService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision])
@TestMixin(HibernateTestMixin)
class RecordServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    boolean calledSetAttribute = false
    Object justSetAttribute
    String _urlRoot = "http://www.example.com/?key="
    int _maxNumImages = 2

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.twimlBuilder = [noResponse: { ->
            new Result(type:ResultType.SUCCESS, success:true, payload:"noResponse")
        }] as TwimlBuilder
        service.authService = [getLoggedInAndActive: { -> s1 }] as AuthService
        service.socketService = [sendItems:{ List<RecordItem> items, String eventName ->
            new ResultList()
        }] as SocketService
        service.metaClass.getRequest = { ->
            [
                setAttribute: { String n, Object o ->
                    calledSetAttribute = true
                    justSetAttribute = o
                }
            ] as HttpServletRequest
        }
        Phone.metaClass.sendMessage = { OutgoingMessage msg, Staff staff ->
            Result res = new Result(type:ResultType.SUCCESS, success:true,
                payload:"sendMessage")
            new ResultList(res)
        }
        Phone.metaClass.startBridgeCall = { Contactable c1, Staff staff ->
            Result res = new Result(type:ResultType.SUCCESS, success:true,
                payload:[
                    methodName:"startBridgeCall",
                    contactable:c1
                ])
            new ResultList(res)
        }
        RecordNote.metaClass.constructor = { Map props ->
            RecordNote note1 = new RecordNote()
            note1.properties = props
            note1.grailsApplication = [getFlatConfig:{
                ['textup.maxNumImages':_maxNumImages]
            }] as GrailsApplication
            note1.storageService = [generateAuthLink:{
                String k, HttpMethod v, Map m=[:] ->
                new Result(success:true, payload:new URL("${_urlRoot}${k}"))
            }] as StorageService
            note1
        }
    }

    def cleanup() {
        super.cleanupData()
    }

    // Status
    // ------

    void "test update status"() {
        given: "an existing receipt"
        String apiId = "iamsosecretApiId"
        [rText1, rText2, rTeText1, rTeText2].each {
            it.addToReceipts(apiId:apiId, receivedByAsString:"1112223333")
            it.save(flush:true, failOnError:true)
        }

        when: "nonexistent apiId"
        Result res = service.updateStatus(ReceiptStatus.SUCCESS, "nonexistentApiId")

        then:
        res.success == false
        res.type ==  ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "recordService.updateStatus.receiptsNotFound"

        when: "existing with invalid status"
        res = service.updateStatus(null, apiId)
        // clear changes to avoid optimistic locking exception
        RecordItemReceipt.withSession { it.clear() }

        then:
        res.success == false
        res.type ==  ResultType.VALIDATION
        res.payload.errorCount == 1

        when: "existing with valid status"
        res = service.updateStatus(ReceiptStatus.SUCCESS, apiId)

        then:
        res.success == true
        res.payload == "noResponse"
    }

    // Create
    // ------

    void "test determine class"() {
        when: "unknown entity"
        Result res = service.determineClass([:])

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == UNPROCESSABLE_ENTITY
        res.payload.code == "recordService.create.unknownType"

        when: "text"
        res = service.determineClass([contents:"hello"])

        then:
        res.success == true
        res.payload == RecordText

        when: "call"
        res = service.determineClass([callContact:c1.id])

        then:
        res.success == true
        res.payload == RecordCall
    }

    void "test create overall"() {
        when: "no phone"
        ResultList resList = service.create(null, [:])

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.code == "recordService.create.noPhone"
        resList.results[0].payload.status == UNPROCESSABLE_ENTITY

        when: "invalid entity"
        resList = service.create(t1.phone, [:])

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.code == "recordService.create.unknownType"
        resList.results[0].payload.status == UNPROCESSABLE_ENTITY

        when: "text"
        Map itemInfo = [contents:"hi", sendToPhoneNumbers:["2223334444"],
            sendToContacts:[tC1.id]]
        resList = service.create(t1.phone, itemInfo)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload == "sendMessage"
    }

    void "test create text"() {
        given:
        int cBaseline = Contact.count()

        when: "no recipients"
        Map itemInfo = [contents:"hi"]
        ResultList resList = service.createText(t1.phone, itemInfo)

        then: "this class does not handle validation for number of recipients"
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload == "sendMessage"
        Contact.count() == cBaseline

        when: "send to a contact"
        itemInfo.sendToContacts = [tC1.id]
        resList = service.createText(t1.phone, itemInfo)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload == "sendMessage"
        Contact.count() == cBaseline

        when: "send to a phone number that belongs to a existing contact"
        itemInfo.sendToPhoneNumbers = [tC1.numbers[0].number]
        resList = service.createText(t1.phone, itemInfo)

        then: "no new contact created"
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload == "sendMessage"
        Contact.count() == cBaseline

        when: "send to a phone number that you've never seen before"
        PhoneNumber newNum = new PhoneNumber(number:"888 888 8888")
        assert newNum.validate()
        assert Contact.listForPhoneAndNum(t1.phone, newNum) == []
        itemInfo.sendToPhoneNumbers = [newNum.number]
        resList = service.createText(t1.phone, itemInfo)

        then: "new contact created for novel number"
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload == "sendMessage"
        Contact.count() == cBaseline + 1
    }

    void "test create call"() {
        when: "try to call multiple"
        Map itemInfo = [callSharedContact:sc2.id, callContact:c1.id]
        ResultList resList = service.createCall(s1.phone, itemInfo)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].type == ResultType.MESSAGE_STATUS
        resList.results[0].payload.code == "recordService.create.canCallOnlyOne"
        resList.results[0].payload.status == BAD_REQUEST

        when: "call contact"
        itemInfo = [callContact:c1.id]
        resList = service.createCall(s1.phone, itemInfo)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.methodName == "startBridgeCall"
        resList.results[0].payload.contactable == c1

        when: "call shared contact"
        itemInfo = [callSharedContact:sc2.contact.id]
        resList = service.createCall(s1.phone, itemInfo)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.methodName == "startBridgeCall"
        resList.results[0].payload.contactable == sc2
    }

    void "test create note for various targets"() {
        given:
        int nBaseline = RecordNote.count()

        when: "creating for a contact"
        Map body = [
            forContact:tC1.id,
            contents:"hi"
        ]
        calledSetAttribute = false
        ResultList<RecordItem> resList = service.createNote(t1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        calledSetAttribute == true
        RecordNote.count() == nBaseline + 1

        when: "creating for a shared contact"
        body = [
            forSharedContact:sc1.contact.id,
            contents:"hi"
        ]
        calledSetAttribute = false
        resList = service.createNote(sc1.sharedWith, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == sc1.contact.record
        calledSetAttribute == true
        RecordNote.count() == nBaseline + 2

        when: "creating for a tag"
        body = [
            forTag:tag1.id,
            contents:"hi"
        ]
        calledSetAttribute = false
        resList = service.createNote(tag1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tag1.record
        calledSetAttribute == true
        RecordNote.count() == nBaseline + 3
    }

    void "test creating note for various info"() {
        given:
        int nBaseline = RecordNote.count()
        int lBaseline = Location.count()

        when: "creating a note after a certain time"
        Map body = [
            forContact:tC1.id,
            contents:"hi",
            after:DateTime.now().minusDays(2)
        ]
        calledSetAttribute = false
        ResultList<RecordItem> resList = service.createNote(t1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        resList.results[0].payload.whenCreated.isBeforeNow()
        calledSetAttribute == true
        RecordNote.count() == nBaseline + 1

        when: "creating a note with location"
        body = [
            forContact:tC1.id,
            contents:"hi",
            location:[
                address:"123 Main Street",
                lat:22G,
                lon:22G
            ]
        ]
        calledSetAttribute = false
        resList = service.createNote(t1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        resList.results[0].payload.location != null
        calledSetAttribute == true
        RecordNote.count() == nBaseline + 2
        Location.count() == lBaseline + 1

        when: "creating a note with image requests"
        body = [
            forContact:tC1.id,
            contents:"hi",
            doImageActions:[
                [
                    action:Constants.NOTE_IMAGE_ACTION_ADD,
                    mimeType:"image/png",
                    sizeInBytes:100
                ],
                [
                    action:Constants.NOTE_IMAGE_ACTION_REMOVE,
                    key:"i am a valid key"
                ]
            ]
        ]
        calledSetAttribute = false
        justSetAttribute = null
        resList = service.createNote(t1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        resList.results[0].payload.imageKeys.size() == 1
        calledSetAttribute == true
        justSetAttribute instanceof List
        justSetAttribute.size() == 1
        RecordNote.count() == nBaseline + 3
    }

    void "test updating note"() {
        given: "baselines and an existing note"
        RecordNote note1 = new RecordNote(record:c1.record)
        note1.save(flush:true, failOnError:true)
        int rBaseline = RecordNoteRevision.count()
        int nBaseline = RecordNote.count()

        when: "updating a nonexistent note"
        Map body = [
            contents: "hello!"
        ]
        Result<RecordItem> res = service.update(-88L, body)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "recordService.update.notFound"
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline

        when: "updating an existing note"
        res = service.update(note1.id, body)

        then: "updated and created a revision"
        res.success == true
        res.payload instanceof RecordNote
        res.payload.id == note1.id
        res.payload.contents == body.contents
        !res.payload.revisions.isEmpty()
        RecordNoteRevision.count() == rBaseline + 1
        RecordNote.count() == nBaseline
    }

    void "test deleting note"() {
        given: "baselines and an existing note"
        RecordNote note1 = new RecordNote(record:c1.record)
        note1.save(flush:true, failOnError:true)
        int rBaseline = RecordNoteRevision.count()
        int nBaseline = RecordNote.count()

        when: "deleting a nonexistent note"
        Result res = service.delete(-88L)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "recordService.delete.notFound"
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline

        when: "deleting an existing note"
        res = service.delete(note1.id)

        then:
        res.success == true
        res.payload == null
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline
        RecordNote.get(note1.id).isDeleted == true
    }
}
