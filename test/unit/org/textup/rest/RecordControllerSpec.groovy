package org.textup.rest

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestFor(RecordController)
@TestMixin(HibernateTestMixin)
class RecordControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test show"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        MockedMethod doShow = MockedMethod.create(controller, "doShow")
        MockedMethod isAllowed = MockedMethod.create(RecordItems, "isAllowed")
        MockedMethod mustFindForId = MockedMethod.create(RecordItems, "mustFindForId")

        when:
        params.id = id
        controller.show()

        then:
        doShow.latestArgs[0] instanceof Closure
        doShow.latestArgs[1] instanceof Closure

        when:
        doShow.latestArgs[0].call()
        doShow.latestArgs[1].call()

        then:
        isAllowed.latestArgs == [id]
        mustFindForId.latestArgs == [id]

        cleanup:
        doShow?.restore()
        isAllowed?.restore()
        mustFindForId?.restore()
    }

    void "test save"() {
        given:
        Long teamId = TestUtils.randIntegerUpTo(88)
        Long pId = TestUtils.randIntegerUpTo(88)
        String tzId = TestUtils.randString()
        TypeMap body = TypeMap.create()

        controller.recordService = GroovyMock(RecordService)
        MockedMethod doSave = MockedMethod.create(controller, "doSave")
        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId") {
            Result.createSuccess(pId)
        }

        when:
        params.timezone = tzId
        params.teamId = teamId
        controller.save()

        then:
        doSave.latestArgs[0] == MarshallerUtils.KEY_RECORD_ITEM
        doSave.latestArgs[1] == request
        doSave.latestArgs[2] == controller.recordService
        doSave.latestArgs[3] instanceof Closure

        when:
        doSave.latestArgs[3].call(body)

        then:
        body.timezone == tzId
        tryGetPhoneId.latestArgs == [teamId]
        RequestUtils.tryGet(RequestUtils.PHONE_ID).payload == pId

        cleanup:
        doSave?.restore()
        tryGetPhoneId?.restore()
    }

    void "test update"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)
        String tzId = TestUtils.randString()
        TypeMap body = TypeMap.create()

        controller.recordService = GroovyMock(RecordService)
        MockedMethod doUpdate = MockedMethod.create(controller, "doUpdate")
        MockedMethod isAllowed = MockedMethod.create(RecordItems, "isAllowed")

        when:
        params.timezone = tzId
        params.id = id
        controller.update()

        then:
        doUpdate.latestArgs[0] == MarshallerUtils.KEY_RECORD_ITEM
        doUpdate.latestArgs[1] == request
        doUpdate.latestArgs[2] == controller.recordService
        doUpdate.latestArgs[3] instanceof Closure

        when:
        doUpdate.latestArgs[3].call(body)

        then:
        body.timezone == tzId
        isAllowed.latestArgs == [id]

        cleanup:
        doUpdate?.restore()
        isAllowed?.restore()
    }

    void "test delete"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        controller.recordService = GroovyMock(RecordService)
        MockedMethod doDelete = MockedMethod.create(controller, "doDelete")
        MockedMethod isAllowed = MockedMethod.create(RecordItems, "isAllowed")

        when:
        params.id = id
        controller.delete()

        then:
        doDelete.latestArgs[0] == controller.recordService
        doDelete.latestArgs[1] instanceof Closure

        when:
        doDelete.latestArgs[1].call()

        then:
        isAllowed.latestArgs == [id]

        cleanup:
        doDelete?.restore()
        isAllowed?.restore()
    }

    void "test responding with pdf"() {
        given:
        String fileName = TestUtils.randString()
        String err1 = TestUtils.randString()
        byte[] randData = TestUtils.randString().bytes

        Result failRes1 = Result.createError([err1], ResultStatus.FORBIDDEN)
        Result res1 = Result.createSuccess(randData)

        when:
        controller.respondWithPdf(fileName, failRes1)

        then:
        response.status == ResultStatus.FORBIDDEN.intStatus
        response.text.contains(err1)

        when:
        response.reset()
        controller.respondWithPdf(fileName, res1)

        then:
        response.status == ResultStatus.OK.intStatus
        response.contentType.contains(ControllerUtils.CONTENT_TYPE_PDF)
        response.contentAsByteArray == randData
    }

    void "test listing overall"() {
        given:
        Long teamId = TestUtils.randIntegerUpTo(88)
        Long pId = TestUtils.randIntegerUpTo(88)
        String tzId = TestUtils.randString()

        DetachedCriteria crit1 = GroovyStub()
        RecordItemRequest iReq = GroovyStub() { getCriteria() >> crit1 }
        controller.pdfService = GroovyMock(PdfService)
        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId") {
            Result.createSuccess(pId)
        }
        MockedMethod buildRecordItemRequest = MockedMethod.create(RecordUtils, "buildRecordItemRequest") {
            Result.createSuccess(iReq)
        }
        MockedMethod respondWithPdf = MockedMethod.create(controller, "respondWithPdf")
        MockedMethod respondWithCriteria = MockedMethod.create(controller, "respondWithCriteria")

        when:
        params.teamId = teamId
        controller.index()

        then:
        tryGetPhoneId.latestArgs == [teamId]
        buildRecordItemRequest.latestArgs == [pId, TypeMap.create(params)]
        respondWithPdf.notCalled
        respondWithCriteria.latestArgs[0] == crit1
        respondWithCriteria.latestArgs[1] == TypeMap.create(params)
        respondWithCriteria.latestArgs[2] instanceof Closure
        respondWithCriteria.latestArgs[3] == MarshallerUtils.KEY_RECORD_ITEM

        when:
        response.reset()
        params.format = ControllerUtils.FORMAT_PDF
        controller.index()

        then:
        tryGetPhoneId.latestArgs == [teamId]
        buildRecordItemRequest.latestArgs == [pId, TypeMap.create(params)]
        1 * controller.pdfService.buildRecordItems(iReq) >> Result.void()
        respondWithPdf.latestArgs[0].contains(".pdf")
        respondWithPdf.latestArgs[1] == Result.void()

        cleanup:
        tryGetPhoneId?.restore()
        buildRecordItemRequest?.restore()
        respondWithPdf?.restore()
        respondWithCriteria?.restore()
    }
}
