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
import org.textup.override.*
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
@TestFor(ContactController)
@TestMixin(HibernateTestMixin)
class ContactControllerSpec extends Specification {

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
        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed")
        MockedMethod mustFindForId = MockedMethod.create(IndividualPhoneRecordWrappers, "mustFindForId")

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

        controller.contactService = GroovyMock(ContactService)
        MockedMethod doSave = MockedMethod.create(controller, "doSave")
        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId")

        when:
        params.teamId = teamId
        controller.save()

        then:
        doSave.latestArgs[0] == MarshallerUtils.KEY_CONTACT
        doSave.latestArgs[1] == request
        doSave.latestArgs[2] == controller.contactService
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

        controller.contactService = GroovyMock(ContactService)
        MockedMethod doUpdate = MockedMethod.create(controller, "doUpdate")
        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed")

        when:
        params.id = id
        controller.update()

        then:
        doUpdate.latestArgs[0] == MarshallerUtils.KEY_CONTACT
        doUpdate.latestArgs[1] == request
        doUpdate.latestArgs[2] == controller.contactService
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

        controller.contactService = GroovyMock(ContactService)
        MockedMethod doDelete = MockedMethod.create(controller, "doDelete")
        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed")

        when:
        params.id = id
        controller.delete()

        then:
        doDelete.latestArgs[0] == controller.contactService
        doDelete.latestArgs[1] instanceof Closure

        when:
        doDelete.latestArgs[1].call()

        then:
        isAllowed.latestArgs == [id]

        cleanup:
        doDelete?.restore()
        isAllowed?.restore()
    }

    void "test building criteria"() {
        given:
        Long pId = TestUtils.randIntegerUpTo(88)
        Long id1 = TestUtils.randIntegerUpTo(88)
        Long id2 = TestUtils.randIntegerUpTo(88)
        PhoneRecordStatus stat1 = PhoneRecordStatus.values()[0]
        TypeMap params1 = TypeMap.create(search: TestUtils.randString(),
            shareStatus: "sharedByMe",
            "ids[]": [id1])
        TypeMap params2 = TypeMap.create(search: TestUtils.randString(),
            shareStatus: "sharedWithMe",
            "ids[]": [id2])
        TypeMap params3 = TypeMap.create(search: TestUtils.randString())

        DetachedJoinableCriteria crit1 = GroovyMock()
        DetachedJoinableCriteria crit2 = GroovyMock()
        Closure closure1 = GroovyMock()
        MockedMethod buildForSharedByIdWithOptions = MockedMethod.create(IndividualPhoneRecordWrappers, "buildForSharedByIdWithOptions") {
            crit1
        }
        MockedMethod buildForPhoneIdWithOptions = MockedMethod.create(IndividualPhoneRecordWrappers, "buildForPhoneIdWithOptions") {
            crit2
        }
        MockedMethod forIds = MockedMethod.create(PhoneRecords, "forIds") {
            closure1
        }

        when:
        DetachedJoinableCriteria retCrit = controller.buildCriteria(pId, [stat1], params1)

        then:
        buildForSharedByIdWithOptions.latestArgs == [pId, params1.search, [stat1]]
        forIds.latestArgs == [[id1]]
        1 * crit1.build(closure1) >> crit1
        retCrit == crit1

        when:
        retCrit = controller.buildCriteria(pId, [stat1], params2)

        then:
        buildForPhoneIdWithOptions.latestArgs == [pId, params2.search, [stat1], true]
        forIds.latestArgs == [[id2]]
        1 * crit2.build(closure1) >> crit2
        retCrit == crit2

        when:
        retCrit = controller.buildCriteria(pId, [stat1], params3)

        then:
        buildForPhoneIdWithOptions.latestArgs == [pId, params3.search, [stat1], false]
        0 * crit2.build(*_)
        retCrit == crit2

        cleanup:
        buildForSharedByIdWithOptions?.restore()
        buildForPhoneIdWithOptions?.restore()
        forIds?.restore()
    }

    void "test listing for ids"() {
        given:
        String err1 = TestUtils.randString()
        Long pId = TestUtils.randIntegerUpTo(88)
        PhoneRecordStatus stat1 = PhoneRecordStatus.values()[0]
        Long teamId = TestUtils.randIntegerUpTo(88)
        TypeMap qParams = TypeMap.create(teamId: teamId)

        MockedMethod tryGetPhoneId = MockedMethod.create(ControllerUtils, "tryGetPhoneId") {
            Result.createError([err1], ResultStatus.BAD_REQUEST)
        }
        MockedMethod respondWithClosures = MockedMethod.create(controller, "respondWithClosures")

        when:
        controller.listForIds([stat1], qParams)

        then:
        tryGetPhoneId.latestArgs == [teamId]
        respondWithClosures.notCalled
        response.text.contains(err1)
        response.status == ResultStatus.BAD_REQUEST.intStatus

        when:
        tryGetPhoneId = MockedMethod.create(tryGetPhoneId) { Result.createSuccess(pId) }
        response.reset()
        controller.listForIds([stat1], qParams)

        then:
        tryGetPhoneId.latestArgs == [teamId]
        respondWithClosures.latestArgs[0] instanceof Closure
        respondWithClosures.latestArgs[1] instanceof Closure
        respondWithClosures.latestArgs[2] == qParams
        respondWithClosures.latestArgs[3] == MarshallerUtils.KEY_CONTACT

        cleanup:
        tryGetPhoneId?.restore()
        respondWithClosures?.restore()
    }

    void "test listing for tag"() {
        given:
        String err1 = TestUtils.randString()
        PhoneRecordStatus stat1 = PhoneRecordStatus.values()[0]
        PhoneRecordStatus stat2 = PhoneRecordStatus.values()[1]

        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.status = stat1
        gpr1.members.addToPhoneRecords(ipr1)

        PhoneRecord.withSession { it.flush() }
        TypeMap qParams = TypeMap.create(tagId: gpr1.id)

        MockedMethod isAllowed = MockedMethod.create(PhoneRecords, "isAllowed") {
            Result.createError([err1], ResultStatus.FORBIDDEN)
        }

        when:
        controller.listForTag([stat1], qParams)

        then:
        isAllowed.latestArgs == [gpr1.id]
        response.status == ResultStatus.FORBIDDEN.intStatus
        response.text.contains(err1)

        when:
        isAllowed = MockedMethod.create(isAllowed) { Result.void() }
        response.reset()
        controller.listForTag([stat1], qParams)

        then:
        isAllowed.latestArgs == [gpr1.id]
        response.json instanceof Collection
        response.json[0].id == ipr1.id

        when:
        response.reset()
        controller.listForTag([stat2], qParams)

        then:
        isAllowed.latestArgs == [gpr1.id]
        response.json[MarshallerUtils.resolveCodeToPlural(MarshallerUtils.KEY_CONTACT)] == []

        cleanup:
        isAllowed?.restore()
    }

    void "test listing overall"() {
        given:
        PhoneRecordStatus stat1 = PhoneRecordStatus.BLOCKED

        MockedMethod listForIds = MockedMethod.create(controller, "listForIds")
        MockedMethod listForTag = MockedMethod.create(controller, "listForTag")

        when:
        controller.index()

        then:
        listForIds.latestArgs == [PhoneRecordStatus.VISIBLE_STATUSES, TypeMap.create(params)]
        listForTag.notCalled

        when:
        response.reset()
        params."status[]" = [stat1]
        params.tagId = TestUtils.randIntegerUpTo(88)
        controller.index()

        then:
        listForIds.hasBeenCalled
        listForTag.latestArgs == [[stat1], TypeMap.create(params)]

        cleanup:
        listForTag?.restore()
        listForIds?.restore()
    }
}
