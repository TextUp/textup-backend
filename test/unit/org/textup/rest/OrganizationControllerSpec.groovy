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
@TestFor(OrganizationController)
@TestMixin(HibernateTestMixin)
class OrganizationControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test index"() {
        given:
        String search = TestUtils.randString()

        DetachedCriteria crit1 = GroovyMock()
        MockedMethod buildForOptions = MockedMethod.create(Organizations, "buildForOptions") {
            crit1
        }
        MockedMethod respondWithCriteria = MockedMethod.create(controller, "respondWithCriteria")

        when:
        params.search = search
        controller.index()

        then:
        buildForOptions.latestArgs == [search, null]
        respondWithCriteria.latestArgs == [crit1, TypeMap.create(params), null, MarshallerUtils.KEY_ORGANIZATION]

        cleanup:
        buildForOptions?.restore()
        respondWithCriteria?.restore()
    }

    void "test show"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        MockedMethod doShow = MockedMethod.create(controller, "doShow")
        MockedMethod mustFindForId = MockedMethod.create(Organizations, "mustFindForId")

        when:
        params.id = id
        controller.show()

        then:
        doShow.latestArgs[0] instanceof Closure
        doShow.latestArgs[1] instanceof Closure

        when:
        def retVal = doShow.latestArgs[0].call()
        doShow.latestArgs[1].call()

        then:
        retVal == Result.void()
        mustFindForId.latestArgs == [id]

        cleanup:
        doShow?.restore()
        mustFindForId?.restore()
    }

    void "test update"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)

        controller.organizationService = GroovyMock(OrganizationService)
        MockedMethod doUpdate = MockedMethod.create(controller, "doUpdate")
        MockedMethod isAllowed = MockedMethod.create(Organizations, "isAllowed")

        when:
        params.id = id
        controller.update()

        then:
        doUpdate.latestArgs[0] == MarshallerUtils.KEY_ORGANIZATION
        doUpdate.latestArgs[1] == request
        doUpdate.latestArgs[2] == controller.organizationService
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
