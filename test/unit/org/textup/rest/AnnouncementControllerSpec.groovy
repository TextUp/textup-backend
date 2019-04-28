package org.textup.rest

import grails.gorm.DetachedCriteria
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
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
@TestFor(AnnouncementController)
@TestMixin(HibernateTestMixin)
class AnnouncementControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test index"() {
        given:
        String err1 = TestUtils.randString()
        Long teamId = TestUtils.randIntegerUpTo(88)
        Long pId = TestUtils.randIntegerUpTo(88)

        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId") {
            Result.createError([err1], ResultStatus.BAD_REQUEST)
        }
        MockedMethod respondWithCriteria = MockedMethod.create(controller, "respondWithCriteria")

        when:
        params.teamId = teamId
        controller.index()

        then:
        tryGetPhoneId.latestArgs == [teamId]
        respondWithCriteria.notCalled
        response.status == ResultStatus.BAD_REQUEST.intStatus
        response.text.contains(err1)

        when:
        tryGetPhoneId = MockedMethod.create(tryGetPhoneId) { Result.createSuccess(pId) }
        response.reset()
        controller.index()

        then:
        tryGetPhoneId.latestArgs == [teamId]
        respondWithCriteria.latestArgs[0] instanceof DetachedCriteria
        respondWithCriteria.latestArgs[1] == params
        respondWithCriteria.latestArgs[2] instanceof Closure
        respondWithCriteria.latestArgs[3] == MarshallerUtils.KEY_ANNOUNCEMENT

        cleanup:
        tryGetPhoneId?.restore()
        respondWithCriteria?.restore()
    }

    void "test show"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        MockedMethod doShow = MockedMethod.create(controller, "doShow")
        MockedMethod isAllowed = MockedMethod.create(FeaturedAnnouncements, "isAllowed")
        MockedMethod mustFindForId = MockedMethod.create(FeaturedAnnouncements, "mustFindForId")

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

        controller.announcementService = GroovyMock(AnnouncementService)
        MockedMethod doSave = MockedMethod.create(controller, "doSave")
        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId")

        when:
        params.teamId = teamId
        controller.save()

        then:
        doSave.latestArgs[0] == MarshallerUtils.KEY_ANNOUNCEMENT
        doSave.latestArgs[1] == request
        doSave.latestArgs[2] == controller.announcementService
        doSave.latestArgs[3] instanceof Closure

        when:
        doSave.latestArgs[3].call()

        then:
        tryGetPhoneId.latestArgs == [teamId]

        cleanup:
        doSave?.restore()
        tryGetPhoneId?.restore()
    }

    void "test update"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        controller.announcementService = GroovyMock(AnnouncementService)
        MockedMethod doUpdate = MockedMethod.create(controller, "doUpdate")
        MockedMethod isAllowed = MockedMethod.create(FeaturedAnnouncements, "isAllowed")

        when:
        params.id = id
        controller.update()

        then:
        doUpdate.latestArgs[0] == MarshallerUtils.KEY_ANNOUNCEMENT
        doUpdate.latestArgs[1] == request
        doUpdate.latestArgs[2] == controller.announcementService
        doUpdate.latestArgs[3] instanceof Closure

        when:
        doUpdate.latestArgs[3].call()

        then:
        isAllowed.latestArgs == [id]

        cleanup:
        doUpdate?.restore()
        isAllowed?.restore()
    }
}
