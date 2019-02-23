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
@TestFor(TagController)
@TestMixin(HibernateTestMixin)
class TagControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test index"() {
        given:
        Long teamId = TestUtils.randIntegerUpTo(88)
        Long pId = TestUtils.randIntegerUpTo(88)

        DetachedCriteria crit1 = GroovyMock()
        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId") {
            Result.createSuccess(pId)
        }
        MockedMethod buildForPhoneIdAndOptions = MockedMethod.create(GroupPhoneRecords, "buildForPhoneIdAndOptions") {
            crit1
        }
        MockedMethod respondWithCriteria = MockedMethod.create(controller, "respondWithCriteria")

        when:
        params.teamId = teamId
        controller.index()

        then:
        tryGetPhoneId.latestArgs == [teamId]
        buildForPhoneIdAndOptions.latestArgs == [pId, null]
        respondWithCriteria.latestArgs[0] == crit1
        respondWithCriteria.latestArgs[1] == params
        respondWithCriteria.latestArgs[2] instanceof Closure
        respondWithCriteria.latestArgs[3] == MarshallerUtils.KEY_TAG

        cleanup:
        tryGetPhoneId?.restore()
        buildForPhoneIdAndOptions?.restore()
        respondWithCriteria?.restore()
    }

    void "test show"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        MockedMethod doShow = MockedMethod.create(controller, "doShow")
        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed")
        MockedMethod mustFindForId = MockedMethod.create(GroupPhoneRecords, "mustFindForId")

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

        controller.tagService = GroovyMock(TagService)
        MockedMethod doSave = MockedMethod.create(controller, "doSave")
        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId")

        when:
        params.teamId = teamId
        controller.save()

        then:
        doSave.latestArgs[0] == MarshallerUtils.KEY_TAG
        doSave.latestArgs[1] == request
        doSave.latestArgs[2] == controller.tagService
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

        controller.tagService = GroovyMock(TagService)
        MockedMethod doUpdate = MockedMethod.create(controller, "doUpdate")
        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed")

        when:
        params.id = id
        controller.update()

        then:
        doUpdate.latestArgs[0] == MarshallerUtils.KEY_TAG
        doUpdate.latestArgs[1] == request
        doUpdate.latestArgs[2] == controller.tagService
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

        controller.tagService = GroovyMock(TagService)
        MockedMethod doDelete = MockedMethod.create(controller, "doDelete")
        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed")

        when:
        params.id = id
        controller.delete()

        then:
        doDelete.latestArgs[0] == controller.tagService
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
