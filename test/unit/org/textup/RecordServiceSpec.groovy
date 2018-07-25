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
import org.textup.rest.TwimlBuilder
import org.textup.type.ReceiptStatus
import org.textup.util.CustomSpec
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordNote
import org.textup.validator.UploadItem
import spock.lang.Shared
import spock.lang.Specification

@TestFor(RecordService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class RecordServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String _urlRoot = "http://www.example.com/?key="
    int _maxNumImages = 2
    boolean _uploadShouldSucceed = true
    boolean _didUpdateRequest = false
    RecordNote _note1
    String _uploadFailureCode = "failCode"

    def setup() {
        super.setupData()
        service.resultFactory = getResultFactory()
        service.twimlBuilder = [noResponse: { ->
            new Result(status:ResultStatus.OK, payload:"noResponse")
        }] as TwimlBuilder
        service.authService = [getLoggedInAndActive: { -> s1 }] as AuthService
        service.socketService = [sendItems:{ List<RecordItem> items, String eventName ->
            new ResultGroup()
        }] as SocketService
        service.metaClass.getRequest = { ->
            [
                setAttribute: { String n, Object o ->
                    _didUpdateRequest = true
                }
            ] as HttpServletRequest
        }
        service.metaClass.createTextHelper = { Phone p1, OutgoingMessage msg, Staff staff ->
            new Result<RecordItem>(status:ResultStatus.CREATED, payload:"sendMessage").toGroup()
        }
        service.metaClass.createCallHelper = { Phone p1, Contactable c1, Staff staff ->
            new Result<RecordCall>(status:ResultStatus.CREATED,
                payload:[
                    methodName:"startBridgeCall",
                    contactable:c1
                ])
        }
        RecordNote.metaClass.constructor = { Map props ->
            RecordNote note1 = new RecordNote()
            note1.properties = props
            note1.grailsApplication = [getFlatConfig:{
                ['textup.maxNumImages':_maxNumImages]
            }] as GrailsApplication
            note1.storageService = [
                generateAuthLink:{ String k ->
                    new Result(status:ResultStatus.OK, payload:new URL("${_urlRoot}${k}"))
                },
                upload: { String objectKey, UploadItem uItem ->
                    if (_uploadShouldSucceed) {
                        new Result(status:ResultStatus.OK,
                            payload:[getETag: { -> _eTag }] as PutObjectResult)
                    }
                    else {
                        getResultFactory().failWithCodeAndStatus(_uploadFailureCode, ResultStatus.BAD_REQUEST)
                    }
                }
            ] as StorageService
            note1
        }

        Record rec = new Record([:])
        assert rec.validate()
        _note1 = new RecordNote(record:rec)
        assert _note1.validate()
        _note1.record.save(flush:true, failOnError:true)
        _note1.save(flush:true, failOnError:true)
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
            it.addToReceipts(apiId:apiId, contactNumberAsString:"1112223333")
            it.save(flush:true, failOnError:true)
        }
        addToMessageSource("recordService.updateStatus.receiptsNotFound")

        when: "nonexistent apiId"
        Result res = service.updateStatus(ReceiptStatus.SUCCESS, "nonexistentApiId")

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "recordService.updateStatus.receiptsNotFound"

        when: "existing with invalid status"
        res = service.updateStatus(null, apiId)
        // clear changes to avoid optimistic locking exception
        RecordItemReceipt.withSession { it.clear() }

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "existing with valid status"
        res = service.updateStatus(ReceiptStatus.SUCCESS, apiId)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == "noResponse"
    }

    // Create
    // ------

    void "test determine class"() {
        given:
        addToMessageSource("recordService.create.unknownType")

        when: "unknown entity"
        Result res = service.determineClass([:])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "recordService.create.unknownType"

        when: "text"
        res = service.determineClass([sendToContacts:[1]])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordText

        when: "note"
        res = service.determineClass([forContact:1])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordNote

        when: "call"
        res = service.determineClass([callContact:c1.id])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordCall
    }

    void "test create overall"() {
        given:
        addToMessageSource(["recordService.create.noPhone", "recordService.create.unknownType"])

        when: "no phone"
        ResultGroup resGroup = service.create(null, [:])

        then:
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "recordService.create.noPhone"
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY

        when: "invalid entity"
        resGroup = service.create(t1.phone, [:])

        then:
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "recordService.create.unknownType"
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY

        when: "text"
        Map itemInfo = [contents:"hi", sendToPhoneNumbers:["2223334444"],
            sendToContacts:[tC1.id]]
        resGroup = service.create(t1.phone, itemInfo)

        then:
        resGroup.anySuccesses == true
        resGroup.successStatus == ResultStatus.CREATED
        resGroup.successes.size() == 1
        resGroup.successes[0].payload == "sendMessage"
    }

    void "test create text"() {
        given:
        int cBaseline = Contact.count()

        when: "no recipients"
        Map itemInfo = [contents:"hi"]
        ResultGroup resGroup = service.createText(t1.phone, itemInfo)

        then: "this class does not handle validation for number of recipients"
        resGroup.anySuccesses == true
        resGroup.successStatus == ResultStatus.CREATED
        resGroup.successes.size() == 1
        resGroup.successes[0].payload == "sendMessage"
        Contact.count() == cBaseline

        when: "send to a contact"
        itemInfo.sendToContacts = [tC1.id]
        resGroup = service.createText(t1.phone, itemInfo)

        then:
        resGroup.anySuccesses == true
        resGroup.successStatus == ResultStatus.CREATED
        resGroup.successes.size() == 1
        resGroup.successes[0].payload == "sendMessage"
        Contact.count() == cBaseline

        when: "send to a phone number that belongs to a existing contact"
        itemInfo.sendToPhoneNumbers = [tC1.numbers[0].number]
        resGroup = service.createText(t1.phone, itemInfo)

        then: "no new contact created"
        resGroup.anySuccesses == true
        resGroup.successStatus == ResultStatus.CREATED
        resGroup.successes.size() == 1
        resGroup.successes[0].payload == "sendMessage"
        Contact.count() == cBaseline

        when: "send to a phone number that you've never seen before"
        PhoneNumber newNum = new PhoneNumber(number:"888 888 8888")
        assert newNum.validate()
        assert Contact.listForPhoneAndNum(t1.phone, newNum) == []
        itemInfo.sendToPhoneNumbers = [newNum.number]
        resGroup = service.createText(t1.phone, itemInfo)

        then: "new contact created for novel number"
        resGroup.anySuccesses == true
        resGroup.successStatus == ResultStatus.CREATED
        resGroup.successes.size() == 1
        resGroup.successes[0].payload == "sendMessage"
        Contact.count() == cBaseline + 1
    }

    void "test create call"() {
        given:
        addToMessageSource("recordService.create.canCallOnlyOne")

        when: "try to call multiple"
        Map itemInfo = [callSharedContact:sc2.id, callContact:c1.id]
        Result<RecordItem> res = service.createCall(s1.phone, itemInfo)

        then:
        res.success == false
        res.errorMessages[0] == "recordService.create.canCallOnlyOne"
        res.status == ResultStatus.BAD_REQUEST

        when: "call contact"
        itemInfo = [callContact:c1.id]
        res = service.createCall(s1.phone, itemInfo)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.methodName == "startBridgeCall"
        res.payload.contactable.contactId == c1.contactId

        when: "call shared contact"
        itemInfo = [callSharedContact:sc2.contact.id]
        res = service.createCall(s1.phone, itemInfo)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.methodName == "startBridgeCall"
        res.payload.contactable.contactId == sc2.contactId
    }

    void "test create note for various targets"() {
        given:
        int nBaseline = RecordNote.count()

        when: "creating for a contact"
        Map body = [
            forContact:tC1.id,
            noteContents:"hi"
        ]
        Result<RecordItem> res = service.createNote(t1.phone, body)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.record == tC1.record
        RecordNote.count() == nBaseline + 1

        when: "creating for a shared contact"
        body = [
            forSharedContact:sc1.contact.id,
            noteContents:"hi"
        ]
        res = service.createNote(sc1.sharedWith, body)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.record == sc1.contact.record
        RecordNote.count() == nBaseline + 2

        when: "creating for a tag"
        body = [
            forTag:tag1.id,
            noteContents:"hi"
        ]
        res = service.createNote(tag1.phone, body)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.record == tag1.record
        RecordNote.count() == nBaseline + 3
    }

    void "test creating note for various info"() {
        given:
        int nBaseline = RecordNote.count()
        int lBaseline = Location.count()

        String contentType = "image/png"
        String data = Base64.encodeBase64String("hello".getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(data)

        addToMessageSource(_uploadFailureCode)

        when: "creating a note after a certain time"
        Map body = [
            forContact:tC1.id,
            noteContents:"hi",
            after:DateTime.now().minusDays(2)
        ]
        Result<RecordItem> res = service.createNote(t1.phone, body)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.record == tC1.record
        res.payload.whenCreated.isBeforeNow()
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
        res = service.createNote(t1.phone, body)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.record == tC1.record
        res.payload.location != null
        RecordNote.count() == nBaseline + 2
        Location.count() == lBaseline + 1

        when: "creating a note with image requests, all success"
        body = [
            forContact:tC1.id,
            noteContents:"hi",
            doImageActions:[
                [
                    action:Constants.MEDIA_ACTION_REMOVE,
                    key:"i am a valid key"
                ],
                [ // valid add image action
                    action:Constants.MEDIA_ACTION_ADD,
                    mimeType:contentType,
                    data:data,
                    checksum:checksum
                ]
            ]
        ]
        _uploadShouldSucceed = true
        _didUpdateRequest = false
        res = service.createNote(t1.phone, body)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.record == tC1.record
        res.payload.imageKeys.size() == 1
        _didUpdateRequest == false // do not set error messages on request object
        RecordNote.count() == nBaseline + 3

        when: "some upload requests fail"
        _uploadShouldSucceed = false
        _didUpdateRequest = false
        res = service.createNote(t1.phone, body)

        then: "image not added and error messages set on request object"
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.record == tC1.record
        res.payload.imageKeys.size() == 0
        _didUpdateRequest == true // set error messages on request object
        RecordNote.count() == nBaseline + 4
    }

    void "test updating note"() {
        given: "baselines and an existing note"
        RecordNote note1 = new RecordNote(record:c1.record)
        note1.save(flush:true, failOnError:true)
        int rBaseline = RecordNoteRevision.count()
        int nBaseline = RecordNote.count()

        addToMessageSource(["recordService.update.notFound", "recordService.update.readOnly"])

        when: "updating a nonexistent note"
        Map body = [
            noteContents: "hello!"
        ]
        Result<RecordItem> res = service.update(-88L, body)

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "recordService.update.notFound"
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline

        when: "updating a readonly note"
        note1.isReadOnly = true
        note1.save(flush:true, failOnError:true)
        res = service.update(note1.id, body)

        then: "forbidden"
        res.success == false
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "recordService.update.readOnly"
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline

        when: "updating an existing note"
        note1.isReadOnly = false
        note1.save(flush:true, failOnError:true)

        DateTime originalWhenChanged = note1.whenChanged
        res = service.update(note1.id, body)
        res.payload.save(flush:true)

        then: "updated and created a revision"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof RecordNote
        res.payload.id == note1.id
        res.payload.whenChanged.isAfter(originalWhenChanged)
        res.payload.noteContents == body.noteContents
        res.payload.revisions.size() == 1
        RecordNoteRevision.count() == rBaseline + 1
        RecordNote.count() == nBaseline

        when: "toggling isDeleted flag via update"
        originalWhenChanged = res.payload.whenChanged
        body = [
            isDeleted: true
        ]
        res = service.update(note1.id, body)
        res.payload.save(flush:true)

        then: "whenChanged unchanged and no additional revisions"
        res.success == true
        res.status == ResultStatus.OK
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
        res.payload.save(flush:true)

        then: "whenChanged unchanged and no additional revisions"
        res.success == true
        res.status == ResultStatus.OK
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

        addToMessageSource(["recordService.delete.notFound", "recordService.delete.readOnly"])

        when: "deleting a nonexistent note"
        Result res = service.delete(-88L)

        then:
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "recordService.delete.notFound"
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline

        when: "deleting a readonly note"
        note1.isReadOnly = true
        note1.save(flush:true, failOnError:true)
        res = service.delete(note1.id)

        then: "forbidden"
        res.success == false
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "recordService.delete.readOnly"
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline

        when: "deleting an existing note"
        note1.isReadOnly = false
        note1.save(flush:true, failOnError:true)
        res = service.delete(note1.id)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
        RecordNoteRevision.count() == rBaseline
        RecordNote.count() == nBaseline
        RecordNote.get(note1.id).isDeleted == true
    }

    void "validating image actions"() {
        given: "a possible image to upload and a valid temp note for the other fields"
        String contentType = "image/png"
        String data = Base64.encodeBase64String("hello".getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(data)

        Map info = [noteContents:"hi"]
        TempRecordNote temp1 = new TempRecordNote(info:info, note:_note1)
        assert temp1.validate() == true

        service.resultFactory.messageSource = mockMessageSourceWithResolvable()

        when: "images actions is not a list"
        Object imageActions = [i:"am not a list"]
        Result res = service.createOrUpdateNote(temp1, imageActions)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.payload == null
        res.errorMessages.size() == 1
        res.errorMessages.any { it.contains("emptyOrNotACollection") }

        when: "an action is not a map"
        imageActions = [
            ["i am not a map"] // not a map
        ]
        res = service.createOrUpdateNote(temp1, imageActions)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.payload == null
        res.errorMessages.size() == 1
        res.errorMessages.any { it.contains("emptyOrNotAMap") }

        when: "an action trying to add an image"
        imageActions = [
            [ // uploading image without a checksum
                action:Constants.MEDIA_ACTION_ADD,
                mimeType:contentType,
                data:data
            ]
        ]
        res = service.createOrUpdateNote(temp1, imageActions)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.payload == null
        res.errorMessages.size() == 1
        res.errorMessages.any { it.contains("actionContainer.invalidActions") }

        when: "an action trying to add an image"
        imageActions = [
            [ // uploading an image with an invalid content type
                action:Constants.MEDIA_ACTION_ADD,
                mimeType:"invalid",
                data:data,
                checksum: checksum
            ]
        ]
        res = service.createOrUpdateNote(temp1, imageActions)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.payload == null
        res.errorMessages.size() == 1
        res.errorMessages.any { it.contains("actionContainer.invalidActions") }

        when: "an action trying to add an image"
        imageActions = [
            [ // uploading an image with invalidly encoded data
                action:Constants.MEDIA_ACTION_ADD,
                mimeType:contentType,
                data:"invalid data that is not base64 encoded",
                checksum: checksum
            ]
        ]
        res = service.createOrUpdateNote(temp1, imageActions)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.payload == null
        res.errorMessages.size() == 1
        res.errorMessages.any { it.contains("actionContainer.invalidActions") }

        when: "an action trying to remove without key"
        imageActions = [
            [ // removing image with specifying an image key
                action:Constants.MEDIA_ACTION_REMOVE,
            ]
        ]
        res = service.createOrUpdateNote(temp1, imageActions)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.payload == null
        res.errorMessages.size() == 1
        res.errorMessages.any { it.contains("actionContainer.invalidActions") }

        when: "an action of an invalid type"
        imageActions = [
            [ // action of invalid type
                action:"i am an invalid action",
            ]
        ]
        res = service.createOrUpdateNote(temp1, imageActions)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.payload == null
        res.errorMessages.size() == 1
        res.errorMessages.any { it.contains("actionContainer.invalidActions") }

        when: "valid image actions"
        int nBaseline = RecordNote.count()
        assert _note1.imageKeys.size() == 0
        imageActions = [
            [ // valid remove action
                action:Constants.MEDIA_ACTION_REMOVE,
                key:"valid image key"
            ],
            [ // valid add image action
                action:Constants.MEDIA_ACTION_ADD,
                mimeType:contentType,
                data:data,
                checksum:checksum
            ]
        ]
        res = service.createOrUpdateNote(temp1, imageActions)

        then:
        res.success == true
        res.status == ResultStatus.OK
        nBaseline == RecordNote.count() // updating an existing note
        _note1.imageKeys.size() == 1
    }

    void "test parsing types"() {
        given: "a record with one of each type"
        Record rec1 = new Record()
        rec1.resultFactory = getResultFactory()
        rec1.save(flush:true, failOnError:true)

        RecordText rText1 = rec1.addText([contents:"text"], null).payload
        RecordCall rCall1 = rec1.addCall([:], null).payload
        RecordNote rNote1 = new RecordNote(record:rec1)
        [rText1, rCall1, rNote1]*.save(flush:true, failOnError:true)

        when:
        List<Class<? extends RecordItem>> clazzes = service.parseTypes(typesFilter)

        then:
        clazzes.size() == numResults

        where:
        typesFilter              | numResults
        ["call"]                 | 1
        ["text"]                 | 1
        ["note"]                 | 1
        ["call", "text"]         | 2
        ["text", "note"]         | 2
        ["call", "text", "note"] | 3
        ["BAD!", "text", "note"] | 2
    }
}
