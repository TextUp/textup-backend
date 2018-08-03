package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestFor(RecordService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
        twimlBuilder(TwimlBuilder)
    }

    def setup() {
        setupData()
        Helpers.metaClass.'static'.getResultFactory = TestHelpers.getResultFactory(grailsApplication)
        Helpers.metaClass.'static'.trySetOnRequest = { String key, Object obj -> new Result() }
        service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
        service.twimlBuilder = TestHelpers.getTwimlBuilder(grailsApplication)
    }

    def cleanup() {
        cleanupData()
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
        service.socketService = Mock(SocketService)
        addToMessageSource("recordService.updateStatus.receiptsNotFound")

        when: "nonexistent apiId"
        Result res = service.updateStatus(ReceiptStatus.SUCCESS, "nonexistentApiId")

        then:
        0 * service.socketService.sendItems(*_)
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "recordService.updateStatus.receiptsNotFound"

        when: "existing with valid status"
        res = service.updateStatus(ReceiptStatus.SUCCESS, apiId)

        then:
        1 * service.socketService.sendItems(*_)
        res.success == true
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml { Response {} }

        when: "existing with invalid status"
        service.resultFactory.messageSource = TestHelpers.mockMessageSourceWithResolvable()
        res = service.updateStatus(null, apiId)

        then:
        0 * service.socketService.sendItems(*_)
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
    }

    // Create
    // ------

    void "test create overall"() {
        given:
        addToMessageSource(["recordService.create.noPhone", "recordService.create.unknownType"])
        String didCall
        service.metaClass.createText = { Phone p1, Map body -> didCall = "text"; new ResultGroup() }
        service.metaClass.createCall = { Phone p1, Map body -> didCall = "call"; new Result() }
        service.metaClass.createNote = { Phone p1, Map body -> didCall = "note"; new Result() }

        when: "no phone"
        ResultGroup resGroup = service.create(null, [:])

        then:
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "recordService.create.noPhone"
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        null == didCall

        when: "invalid entity"
        resGroup = service.create(t1.phone.id, [:])

        then:
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "recordService.create.unknownType"
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY
        null == didCall

        when: "text"
        Map itemInfo = [contents:"hi", sendToPhoneNumbers:["2223334444"],
            sendToContacts:[tC1.id]]
        service.create(t1.phone.id, itemInfo)

        then:
        "text" == didCall

        when: "call"
        itemInfo = [callContact:8L]
        service.create(t1.phone.id, itemInfo)

        then:
        "call" == didCall

        when: "note"
        itemInfo = [forContact:8L]
        service.create(t1.phone.id, itemInfo)

        then:
        "note" == didCall
    }

    void "test check outgoing message recipients"() {
        given:
        int maxNumRecips = 1
        service.grailsApplication = Mock(GrailsApplication)
        service.grailsApplication.flatConfig >> ["textup.maxNumText": maxNumRecips]
        OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage()
        addToMessageSource(["recordService.create.atLeastOneRecipient", "recordService.create.tooManyForText"])

        when: "no recipients"
        Result<OutgoingMessage> res = service.checkOutgoingMessageRecipients(msg1)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "recordService.create.atLeastOneRecipient"

        when: "too many recipients"
        msg1.contacts.recipients = [c1, c1_1, c2]
        res = service.checkOutgoingMessageRecipients(msg1)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "recordService.create.tooManyForText"

        when: "valid number of recipients"
        msg1.contacts.recipients = [c1]
        res = service.checkOutgoingMessageRecipients(msg1)

        then:
        res.status == ResultStatus.OK
    }

    void "test building outgoing message"() {
        given:
        int cBaseline = Contact.count()
        int cnBaseline = ContactNumber.count()
        service.resultFactory.messageSource = TestHelpers.mockMessageSourceWithResolvable()

        when: "contacts not belonging to me"
        Map body = [contents: "hi", sendToContacts: [c2.id]]
        assert c2.phone != p1
        Result<OutgoingMessage> res = service.buildOutgoingMessage(p1, body, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "expired shared contacts"
        assert sc2.sharedWith == p1
        body = [contents: "hi", sendToSharedContacts: [sc2.contactId]]
        sc2.stopSharing()
        sc2.save(flush: true, failOnError: true)
        res = service.buildOutgoingMessage(p1, body, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "not my tags"
        body = [contents: "hi", sendToTags: [tag2.id]]
        assert tag2.phone != p1
        res = service.buildOutgoingMessage(p1, body, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "invalid phone numbers"
        body = [contents: "hi", sendToPhoneNumbers: ["i am not a valid phone number"]]
        res = service.buildOutgoingMessage(p1, body, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "valid, no recipients"
        service.resultFactory.messageSource = TestHelpers.mockMessageSource()
        body = [contents: "hi"]
        res = service.buildOutgoingMessage(p1, body, null)

        then: "valid, checking for number of recipients happens in a later step"
        res.status == ResultStatus.OK
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "valid, with recipients"
        addToMessageSource("outgoingMessage.getName.contactId")
        sc2.startSharing(ContactStatus.ACTIVE, SharePermission.DELEGATE)
        sc2.save(flush: true, failOnError: true)
        body = [
            contents: "hi",
            sendToContacts: [c1.id],
            sendToSharedContacts: [sc2.contactId],
            sendToTags: [tag1.id],
            sendToPhoneNumbers: [TestHelpers.randPhoneNumber()]
        ]
        res = service.buildOutgoingMessage(p1, body, null)
        Contact.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        Contact.count() == cBaseline + 1
        ContactNumber.count() == cnBaseline + 1
    }

    void "test create text"() {
        given:
        service.mediaService = Mock(MediaService)
        service.storageService = Mock(StorageService)
        service.authService = Mock(AuthService)
        Phone mockPhone = Mock(Phone)
        service.metaClass.buildOutgoingMessage = { Phone p1, Map body, MediaInfo mInfo = null ->
            OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage()
            msg1.contacts.recipients = [c1]
            new Result(payload: msg1)
        }
        addToMessageSource("outgoingMessage.getName.contactId")

        when: "no recipients"
        service.createText(mockPhone, null)

        then: "this class does not handle validation for number of recipients"
        1 * service.mediaService.hasMediaActions(*_) >> false
        0 * service.mediaService.handleActions(*_)
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        1 * service.authService.loggedInAndActive
        1 * mockPhone.sendMessage(*_) >> new ResultGroup()

        when: "with media actions"
        service.createText(mockPhone, null)

        then:
        1 * service.mediaService.hasMediaActions(*_) >> true
        1 * service.mediaService.handleActions(*_) >> new Result(payload: new MediaInfo())
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        1 * service.authService.loggedInAndActive
        1 * mockPhone.sendMessage(*_) >> new ResultGroup()
    }

    void "test create call"() {
        given:
        service.authService = Mock(AuthService)
        addToMessageSource("recordService.create.atLeastOneRecipient")
        int numTimesCalled = 0
        service.metaClass.createCallHelper = { Phone p1, Contactable c1, Staff staff ->
            numTimesCalled++; new Result();
        }

        when: "try to call none"
        Map itemInfo = [:]
        Result<RecordItem> res = service.createCall(s1.phone, itemInfo)

        then:
        res.success == false
        res.errorMessages[0] == "recordService.create.atLeastOneRecipient"
        res.status == ResultStatus.BAD_REQUEST
        0 * service.authService.loggedInAndActive
        0 == numTimesCalled

        when: "try to call multiple"
        itemInfo = [callSharedContact: sc2.id, callContact: c1.id]
        service.createCall(s1.phone, itemInfo)

        then: "valid because checking for multiple has already happened in the controller"
        1 * service.authService.loggedInAndActive
        1 == numTimesCalled

        when: "call contact"
        itemInfo = [callContact: c1.id]
        service.createCall(s1.phone, itemInfo)

        then:
        1 * service.authService.loggedInAndActive
        2 == numTimesCalled

        when: "call shared contact"
        itemInfo = [callSharedContact: sc2.contact.id]
        service.createCall(s1.phone, itemInfo)

        then:
        1 * service.authService.loggedInAndActive
        3 == numTimesCalled
    }

    void "test checking note target"() {
        given:
        [sc1, tC1, tag1].each { it.resultFactory = TestHelpers.getResultFactory(grailsApplication) }

        when: "no recipients"
        Map body = [:]
        Result<RecordItem> res = service.checkNoteTarget(t1.phone, body)

        then: "error"
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages.contains("recordService.create.atLeastOneRecipient")

        when: "multiple recipients"
        body = [forContact: tC1.id, forSharedContact: sc1.contactId]
        res = service.checkNoteTarget(sc1.sharedWith, body)
        RecordNote.withSession { it.flush() }

        then: "arbitrarily choose one first, still valid, multiple recipients check happens in controller"
        res.status == ResultStatus.OK
        res.payload instanceof Record

        when: "creating for a contact"
        body = [forContact: tC1.id]
        res = service.checkNoteTarget(t1.phone, body)
        RecordNote.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload == tC1.record

        when: "creating for a shared contact"
        body = [forSharedContact: sc1.contactId]
        res = service.checkNoteTarget(sc1.sharedWith, body)
        RecordNote.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload == sc1.contact.record

        when: "creating for a tag"
        body = [forTag: tag1.id]
        res = service.checkNoteTarget(tag1.phone, body)
        RecordNote.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload == tag1.record
    }

    void "test merging note for non-object fields"() {
        given:
        Record rec = new Record()
        rec.save(flush: true, failOnError: true)
        RecordNote note1 = new RecordNote(record: rec)
        assert note1.validate()
        service.mediaService = Mock(MediaService)
        service.authService = Mock(AuthService)
        service.storageService = Mock(StorageService)

        when:
        Map body = [
            after:DateTime.now().minusDays(2),
            noteContents: UUID.randomUUID().toString(),
            isDeleted: true
        ]
        Result<RecordNote> res = service.mergeNote(note1, body)

        then:
        1 * service.mediaService.hasMediaActions(*_) >> false
        1 * service.authService.loggedInAndActive >> s1
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        res.status == ResultStatus.OK
        res.payload == note1
        res.payload.whenCreated.isBeforeNow()
        res.payload.noteContents == body.noteContents
        res.payload.isDeleted == body.isDeleted
        res.payload.revisions == null // no revisions for new notes!
    }

    void "test merging note for object fields"() {
        given:
        Record rec = new Record()
        assert rec.save(flush: true, failOnError: true)
        RecordNote note1 = new RecordNote(record: rec)
        assert note1.save(flush: true, failOnError: true)
        service.mediaService = Mock(MediaService)
        service.authService = Mock(AuthService)
        service.storageService = Mock(StorageService)
        int lBaseline = Location.count()
        int mBaseline = MediaInfo.count()

        when: "with location"
        Map body = [location: [address:"123 Main Street", lat:22G, lon:22G]]
        Result<RecordNote> res = service.mergeNote(note1, body)
        RecordNote.withSession { it.flush() }

        then:
        1 * service.mediaService.hasMediaActions(*_) >> false
        0 * service.mediaService.handleActions(*_)
        1 * service.authService.loggedInAndActive >> s1
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        res.status == ResultStatus.OK
        res.payload == note1
        res.payload.location instanceof Location
        res.payload.revisions instanceof Collection // existing note creates revisions
        res.payload.revisions.size() == 1
        Location.count() == lBaseline + 1
        MediaInfo.count() == mBaseline

        when: "with media"
        res = service.mergeNote(note1, [:])
        RecordNote.withSession { it.flush() }

        then:
        1 * service.mediaService.hasMediaActions(*_) >> true
        1 * service.mediaService.handleActions(*_) >> { mInfo1, closure1, body1 ->
            mInfo1.addToMediaElements(TestHelpers.buildMediaElement())
            assert mInfo1.save()
            new Result(payload: mInfo1)
        }
        1 * service.authService.loggedInAndActive >> s1
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        res.status == ResultStatus.OK
        res.payload == note1
        res.payload.location instanceof Location
        res.payload.revisions instanceof Collection // existing note creates revisions
        res.payload.revisions.size() == 2
        Location.count() == lBaseline + 2 // each revision creates its own duplicate location
        MediaInfo.count() == mBaseline + 1
    }

    void "test creating note overall"() {
        given:
        service.mediaService   = Mock(MediaService)
        service.authService    = Mock(AuthService)
        service.storageService = Mock(StorageService)
        int lBaseline          = Location.count()
        int mBaseline          = MediaInfo.count()
        int nBaseline          = RecordNote.count()
        int revBaseline        = RecordNoteRevision.count()
        c1.resultFactory       = TestHelpers.getResultFactory(grailsApplication)

        when:
        Map body = [
            forContact: c1.id,
            noteContents: UUID.randomUUID().toString(),
            location: [address:"123 Main Street", lat:22G, lon:22G]
            // mock having media actions
        ]
        Result<RecordItem> res = service.createNote(p1, body)

        then:
        1 * service.mediaService.hasMediaActions(*_) >> true
        1 * service.mediaService.handleActions(*_) >> { mInfo1, closure1, body1 ->
            mInfo1.addToMediaElements(TestHelpers.buildMediaElement())
            assert mInfo1.save()
            new Result(payload: mInfo1)
        }
        1 * service.authService.loggedInAndActive >> s1
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        Location.count()           == lBaseline + 1
        MediaInfo.count()          == mBaseline + 1
        RecordNote.count()         == nBaseline + 1
        RecordNoteRevision.count() == revBaseline
    }

    void "test updating note overall"() {
        given:
        service.mediaService                        = Mock(MediaService)
        service.authService                         = Mock(AuthService)
        service.storageService                      = Mock(StorageService)
        Record rec                                  = new Record()
        assert rec.save(flush: true, failOnError: true)
        RecordNote note1 = new RecordNote(record:rec,
            noteContents: "hi",
            location: new Location(address: "hi", lat: 0G, lon: 0G),
            author: s1.toAuthor())
        assert note1.save(flush: true, failOnError: true)
        int lBaseline   = Location.count()
        int mBaseline   = MediaInfo.count()
        int nBaseline   = RecordNote.count()
        int revBaseline = RecordNoteRevision.count()

        when: "nonexistent note"
        Result<RecordItem> res = service.update(-88L, null)

        then:
        res.status                 == ResultStatus.NOT_FOUND
        res.errorMessages[0]       == "recordService.update.notFound"
        Location.count()           == lBaseline
        MediaInfo.count()          == mBaseline
        RecordNote.count()         == nBaseline
        RecordNoteRevision.count() == revBaseline

        when: "note is read only"
        note1.isReadOnly = true
        note1.save(flush:true, failOnError:true)
        res = service.update(note1.id, [:])

        then:
        res.status                 == ResultStatus.FORBIDDEN
        res.errorMessages[0]       == "recordService.update.readOnly"
        Location.count()           == lBaseline
        MediaInfo.count()          == mBaseline
        RecordNote.count()         == nBaseline
        RecordNoteRevision.count() == revBaseline

        when: "no longer read only, toggle deleted flag"
        note1.isReadOnly = false
        note1.save(flush:true, failOnError:true)
        Map body = [isDeleted: true]
        res      = service.update(note1.id, body)

        then: "updated but no revisions"
        1 * service.mediaService.hasMediaActions(*_) >> false
        1 * service.authService.loggedInAndActive >> s1
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        res.status                 == ResultStatus.OK
        res.payload                == note1
        res.payload.isDeleted      == true
        Location.count()           == lBaseline
        MediaInfo.count()          == mBaseline
        RecordNote.count()         == nBaseline
        RecordNoteRevision.count() == revBaseline


        when: "update contents"
        body = [noteContents: UUID.randomUUID().toString()]
        res  = service.update(note1.id, body)

        then:
        1 * service.mediaService.hasMediaActions(*_) >> false
        1 * service.authService.loggedInAndActive >> s1
        1 * service.storageService.uploadAsync(*_) >> new ResultGroup()
        res.status                 == ResultStatus.OK
        res.payload                == note1
        res.payload.noteContents   == body.noteContents
        Location.count()           == lBaseline + 1
        MediaInfo.count()          == mBaseline
        RecordNote.count()         == nBaseline
        RecordNoteRevision.count() == revBaseline + 1
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

    // Identification
    // --------------

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

    void "test parsing types"() {
        given: "a record with one of each type"
        Record rec1 = new Record()
        rec1.resultFactory = getResultFactory()
        rec1.save(flush:true, failOnError:true)

        RecordText rText1 = rec1.storeOutgoingText("hi").payload
        RecordCall rCall1 = rec1.storeOutgoingCall().payload
        RecordNote rNote1 = new RecordNote(record: rec1)
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
