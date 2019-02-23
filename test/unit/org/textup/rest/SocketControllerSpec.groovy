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
@TestFor(SocketController)
@TestMixin(HibernateTestMixin)
class SocketControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test save"() {
        given:
        String channelName = TestUtils.randString()
        String socketId = TestUtils.randString()

        controller.socketService = GroovyMock(SocketService)
        MockedMethod respondWithResult = MockedMethod.create(controller, "respondWithResult")

        when:
        params.channel_name = channelName
        params.socket_id = socketId
        controller.save()

        then:
        1 * controller.socketService.authenticate(channelName, socketId) >> Result.void()
        respondWithResult.latestArgs == [Result.void()]

        cleanup:
        respondWithResult?.restore()
    }
}
