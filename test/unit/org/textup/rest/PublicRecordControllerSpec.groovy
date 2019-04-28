package org.textup.rest

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
@TestFor(PublicRecordController)
@TestMixin(HibernateTestMixin)
class PublicRecordControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test save"() {
        given:
        controller.callbackService = GroovyMock(CallbackService)
        controller.callbackStatusService = GroovyMock(CallbackStatusService)
        controller.threadService = GroovyMock(ThreadService)
        MockedMethod validate = MockedMethod.create(TwilioUtils, "validate") { Result.void() }

        when:
        controller.save()

        then:
        validate.latestArgs == [request, TypeMap.create(params)]
        1 * controller.callbackService.process(TypeMap.create(params)) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus

        when:
        params.clear()
        response.reset()
        params[CallbackUtils.PARAM_HANDLE] = CallbackUtils.STATUS
        controller.save()

        then:
        validate.latestArgs == [request, TypeMap.create(params)]
        1 * controller.threadService.delay(*_) >> { args -> args[2].call() }
        1 * controller.callbackStatusService.process(TypeMap.create(params))
        response.status == ResultStatus.OK.intStatus
        response.text == "<Response></Response>"

        cleanup:
        validate?.restore()
    }
}
