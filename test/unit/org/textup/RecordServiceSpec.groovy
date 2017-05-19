package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.types.ReceiptStatus
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.UploadItem
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

    String _urlRoot = "http://www.example.com/?key="
    int _maxNumImages = 2
    boolean _uploadShouldSucceed = true
    boolean _didUpdateRequest = false

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
                    _didUpdateRequest = true
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
            note1.storageService = [
                generateAuthLink:{ String k ->
                    new Result(success:true, payload:new URL("${_urlRoot}${k}"))
                },
                upload: { String objectKey, UploadItem uItem ->
                    new Result(success:_uploadShouldSucceed,
                        payload:[getETag: { -> _eTag }] as PutObjectResult)
                }
            ] as StorageService
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
        res = service.determineClass([sendToContacts:[1]])

        then:
        res.success == true
        res.payload == RecordText

        when: "note"
        res = service.determineClass([forContact:1])

        then:
        res.success == true
        res.payload == RecordNote

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
            noteContents:"hi"
        ]
        ResultList<RecordItem> resList = service.createNote(t1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        RecordNote.count() == nBaseline + 1

        when: "creating for a shared contact"
        body = [
            forSharedContact:sc1.contact.id,
            noteContents:"hi"
        ]
        resList = service.createNote(sc1.sharedWith, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == sc1.contact.record
        RecordNote.count() == nBaseline + 2

        when: "creating for a tag"
        body = [
            forTag:tag1.id,
            noteContents:"hi"
        ]
        resList = service.createNote(tag1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tag1.record
        RecordNote.count() == nBaseline + 3
    }

    void "test creating note for various info"() {
        given:
        int nBaseline = RecordNote.count()
        int lBaseline = Location.count()

        String contentType = "image/png"
        String data = Base64.encodeBase64String("hello".getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(data)

        when: "creating a note after a certain time"
        Map body = [
            forContact:tC1.id,
            noteContents:"hi",
            after:DateTime.now().minusDays(2)
        ]
        ResultList<RecordItem> resList = service.createNote(t1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        resList.results[0].payload.whenCreated.isBeforeNow()
        RecordNote.count() == nBaseline + 1

        when: "creating a note with location"
        body = [
            forContact:tC1.id,
            noteContents:"hi",
            location:[
                address:"123 Main Street",
                lat:22G,
                lon:22G
            ]
        ]
        resList = service.createNote(t1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        resList.results[0].payload.location != null
        RecordNote.count() == nBaseline + 2
        Location.count() == lBaseline + 1

        when: "creating a note with image requests, all success"
        body = [
            forContact:tC1.id,
            noteContents:"hi",
            doImageActions:[
                [
                    action:Constants.NOTE_IMAGE_ACTION_REMOVE,
                    key:"i am a valid key"
                ],
                [ // valid add image action
                    action:Constants.NOTE_IMAGE_ACTION_ADD,
                    mimeType:contentType,
                    data:data,
                    checksum:checksum
                ]
            ]
        ]
        _uploadShouldSucceed = true
        _didUpdateRequest = false
        resList = service.createNote(t1.phone, body)

        then:
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        resList.results[0].payload.imageKeys.size() == 1
        _didUpdateRequest == false // do not set error messages on request object
        RecordNote.count() == nBaseline + 3

        when: "some upload requests fail"
        _uploadShouldSucceed = false
        _didUpdateRequest = false
        resList = service.createNote(t1.phone, body)

        then: "image not added and error messages set on request object"
        resList.isAnySuccess == true
        resList.results.size() == 1
        resList.results[0].payload.record == tC1.record
        resList.results[0].payload.imageKeys.size() == 0
        _didUpdateRequest == true // set error messages on request object
        RecordNote.count() == nBaseline + 4
    }

    void "test updating note"() {
        given: "baselines and an existing note"
        RecordNote note1 = new RecordNote(record:c1.record)
        note1.save(flush:true, failOnError:true)
        int rBaseline = RecordNoteRevision.count()
        int nBaseline = RecordNote.count()

        when: "updating a nonexistent note"
        Map body = [
            noteContents: "hello!"
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
        DateTime originalWhenChanged = note1.whenChanged
        res = service.update(note1.id, body)
        note1.save(flush:true, failOnError:true)

        then: "updated and created a revision"
        res.success == true
        res.payload instanceof RecordNote
        res.payload.id == note1.id
        res.payload.whenChanged.isAfter(originalWhenChanged)
        res.payload.noteContents == body.noteContents
        res.payload.revisions.size() == 1
        RecordNoteRevision.count() == rBaseline + 1
        RecordNote.count() == nBaseline

        when: "toggling isDeleted flag via update"
        originalWhenChanged = note1.whenChanged
        body = [
            isDeleted: true
        ]
        res = service.update(note1.id, body)
        note1.save(flush:true, failOnError:true)

        then: "whenChanged unchanged and no additional revisions"
        res.success == true
        res.payload instanceof RecordNote
        res.payload.id == note1.id
        res.payload.whenChanged.isEqual(originalWhenChanged)
        res.payload.isDeleted == true
        res.payload.revisions.size() == 1
        RecordNoteRevision.count() == rBaseline + 1
        RecordNote.count() == nBaseline

        when: "toggling isDeleted flag via update"
        body = [
            isDeleted: false
        ]
        res = service.update(note1.id, body)
        note1.save(flush:true, failOnError:true)

        then: "whenChanged unchanged and no additional revisions"
        res.success == true
        res.payload instanceof RecordNote
        res.payload.id == note1.id
        res.payload.whenChanged.isEqual(originalWhenChanged)
        res.payload.isDeleted == false
        res.payload.revisions.size() == 1
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
