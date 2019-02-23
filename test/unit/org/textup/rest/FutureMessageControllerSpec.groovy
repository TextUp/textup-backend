package org.textup.rest

import grails.plugin.jodatime.converters.JodaConverters
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
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
@TestFor(FutureMessageController)
@TestMixin(HibernateTestMixin)
class FutureMessageControllerSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
    }

    void "test index"() {
    	given:
    	PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
    	spr1.permission = SharePermission.NONE
    	PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord()
    	GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

    	FutureMessage fMsg1 = TestUtils.buildFutureMessage(spr2.record)
    	FutureMessage fMsg2 = TestUtils.buildFutureMessage(gpr1.record)

    	MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed") { Result.void() }

    	when:
    	params.contactId = spr1.id
    	Result res = controller.index()

    	then:
    	isAllowed.latestArgs == [spr1.id]
    	response.status == ResultStatus.FORBIDDEN.intStatus
    	response.text.contains("phoneRecordWrapper.insufficientPermission")
        RequestUtils.tryGet(RequestUtils.PHONE_RECORD_ID).payload == spr1.id

    	when:
    	params.clear()
    	response.reset()
    	params.contactId = spr2.id
    	res = controller.index()

    	then:
    	isAllowed.latestArgs == [spr2.id]
    	response.status == ResultStatus.OK.intStatus
    	response.json[0].id == fMsg1.id
        RequestUtils.tryGet(RequestUtils.PHONE_RECORD_ID).payload == spr2.id

    	when:
    	params.clear()
    	response.reset()
    	params.tagId = gpr1.id
    	res = controller.index()

    	then:
    	isAllowed.latestArgs == [gpr1.id]
    	response.status == ResultStatus.OK.intStatus
    	response.json[0].id == fMsg2.id
        RequestUtils.tryGet(RequestUtils.PHONE_RECORD_ID).payload == gpr1.id

    	cleanup:
    	isAllowed?.restore()
    }

    void "test show"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        MockedMethod doShow = MockedMethod.create(controller, "doShow")
        MockedMethod isAllowed = MockedMethod.create(FutureMessages, "isAllowed")
        MockedMethod mustFindForId = MockedMethod.create(FutureMessages, "mustFindForId")

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
        Long contactId = TestUtils.randIntegerUpTo(88)
        Long tagId = TestUtils.randIntegerUpTo(88)

        controller.futureMessageService = GroovyMock(FutureMessageService)
        MockedMethod doSave = MockedMethod.create(controller, "doSave")
        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed")

        when:
        params.tagId = tagId
        controller.save()

        then:
        doSave.latestArgs[0] == MarshallerUtils.KEY_FUTURE_MESSAGE
        doSave.latestArgs[1] == request
        doSave.latestArgs[2] == controller.futureMessageService
        doSave.latestArgs[3] instanceof Closure

        when:
        doSave.latestArgs[3].call()

        then:
        isAllowed.latestArgs == [tagId]
        RequestUtils.tryGet(RequestUtils.PHONE_RECORD_ID).payload == tagId

        when:
        response.reset()
        params.contactId = contactId
        controller.save()

        then:
        doSave.latestArgs[0] == MarshallerUtils.KEY_FUTURE_MESSAGE
        doSave.latestArgs[1] == request
        doSave.latestArgs[2] == controller.futureMessageService
        doSave.latestArgs[3] instanceof Closure

        when:
        doSave.latestArgs[3].call()

        then:
        isAllowed.latestArgs == [contactId]
        RequestUtils.tryGet(RequestUtils.PHONE_RECORD_ID).payload == contactId

        cleanup:
        doSave?.restore()
        isAllowed?.restore()
    }

    void "test update"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        controller.futureMessageService = GroovyMock(FutureMessageService)
        MockedMethod doUpdate = MockedMethod.create(controller, "doUpdate")
        MockedMethod isAllowed = MockedMethod.create(FutureMessages, "isAllowed")

        when:
        params.id = id
        controller.update()

        then:
        doUpdate.latestArgs[0] == MarshallerUtils.KEY_FUTURE_MESSAGE
        doUpdate.latestArgs[1] == request
        doUpdate.latestArgs[2] == controller.futureMessageService
        doUpdate.latestArgs[3] instanceof Closure

        when:
        doUpdate.latestArgs[3].call()

        then:
        isAllowed.latestArgs == [id]

        cleanup:
        doUpdate?.restore()
        isAllowed?.restore()
    }

    void "test delete"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        controller.futureMessageService = GroovyMock(FutureMessageService)
        MockedMethod doDelete = MockedMethod.create(controller, "doDelete")
        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed")

        when:
        params.id = id
        controller.delete()

        then:
        doDelete.latestArgs[0] == controller.futureMessageService
        doDelete.latestArgs[1] instanceof Closure

        when:
        doDelete.latestArgs[1].call()

        then:
        isAllowed.latestArgs == [id]

        cleanup:
        doDelete?.restore()
        isAllowed?.restore()
    }
}
