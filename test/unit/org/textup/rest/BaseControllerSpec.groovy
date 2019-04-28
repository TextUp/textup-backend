package org.textup.rest

import grails.gorm.DetachedCriteria
import grails.plugin.jodatime.converters.JodaConverters
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import javax.servlet.http.HttpServletRequest
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
@TestFor(BaseController)
@TestMixin(HibernateTestMixin)
class BaseControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
        JodaConverters.registerJsonAndXmlMarshallers()
    }

    void "test default endpoints"() {
        when:
        controller.index()

        then:
        response.status == ResultStatus.METHOD_NOT_ALLOWED.intStatus

        when:
        response.reset()
        controller.show()

        then:
        response.status == ResultStatus.METHOD_NOT_ALLOWED.intStatus

        when:
        response.reset()
        controller.save()

        then:
        response.status == ResultStatus.METHOD_NOT_ALLOWED.intStatus

        when:
        response.reset()
        controller.update()

        then:
        response.status == ResultStatus.METHOD_NOT_ALLOWED.intStatus

        when:
        response.reset()
        controller.delete()

        then:
        response.status == ResultStatus.METHOD_NOT_ALLOWED.intStatus
    }

    void "test returning in json format"() {
        given:
        Map data = [hello: "world"]

        when:
        controller.withJsonFormat { controller.respond(data) }

        then:
        response.text == '{"hello":"world"}'
        response.json == data
    }

    void "test rendering status"() {
        given:
        ResultStatus stat1 = ResultStatus.values()[0]

        when:
        controller.renderStatus(stat1)

        then:
        response.status == stat1.intStatus
    }

    void "test responding with result"() {
        given:
        String err1 = TestUtils.randString()
        Location loc1 = TestUtils.buildLocation()

        Result failRes1 = Result.createError([err1], ResultStatus.BAD_REQUEST)
        Result res1 = Result.createSuccess({ Hello { World() } })
        Result res2 = Result.void()
        Result res3 = Result.createSuccess(loc1)

        when: "error"
        controller.respondWithResult(failRes1)

        then:
        response.status == failRes1.status.intStatus
        response.text.contains(err1)

        when: "closure"
        response.reset()
        controller.respondWithResult(res1)

        then:
        response.status == res1.status.intStatus
        response.text == "<Hello><World/></Hello>"

        when: "no content"
        response.reset()
        controller.respondWithResult(res2)

        then:
        response.status == res2.status.intStatus
        response.text == ""

        when: "success"
        response.reset()
        controller.respondWithResult(res3)

        then:
        response.status == res3.status.intStatus
        response.json.id == loc1.id
    }

    void "test default delete action"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)
        String err1 = TestUtils.randString()
        ResultStatus failStat1 = ResultStatus.BAD_REQUEST

        ManagesDomain.Deleter service = GroovyMock()

        when:
        controller.doDelete(service) { Result.createSuccess(id) }

        then:
        1 * service.tryDelete(id) >> Result.createError([err1], failStat1)
        response.text.contains(err1)
        response.status == failStat1.intStatus

        when:
        response.reset()
        controller.doDelete(service) { Result.createSuccess(id) }

        then:
        1 * service.tryDelete(id) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus
    }

    void "test default update action"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)
        String key = TestUtils.randString()
        TypeMap body = TestUtils.randTypeMap()

        HttpServletRequest req = GroovyMock()
        ManagesDomain.Updater service = GroovyMock()
        MockedMethod tryGetJsonBody = MockedMethod.create(RequestUtils, "tryGetJsonBody") {
            Result.createSuccess(body)
        }

        when:
        controller.doUpdate(key, req, service) { Result.createSuccess(id) }

        then:
        tryGetJsonBody.latestArgs == [req, key]
        1 * service.tryUpdate(id, body) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus

        cleanup:
        tryGetJsonBody?.restore()
    }

    void "test default save action"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)
        String key = TestUtils.randString()
        TypeMap body = TestUtils.randTypeMap()

        HttpServletRequest req = GroovyMock()
        ManagesDomain.Creater service = GroovyMock()
        MockedMethod tryGetJsonBody = MockedMethod.create(RequestUtils, "tryGetJsonBody") {
            Result.createSuccess(body)
        }

        when:
        controller.doSave(key, req, service) { Result.createSuccess(id) }

        then:
        tryGetJsonBody.latestArgs == [req, key]
        1 * service.tryCreate(id, body) >> Result.void()
        response.status == ResultStatus.NO_CONTENT.intStatus

        cleanup:
        tryGetJsonBody?.restore()
    }

    void "test default show action"() {
        given:
        Location loc1 = TestUtils.buildLocation()

        when:
        controller.doShow({ Result.void() }, { Result.createSuccess(loc1) })

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.id == loc1.id
    }

    void "test responding with closures"() {
        given:
        int numTotal = TestUtils.randIntegerUpTo(88, true)
        Location loc1 = TestUtils.buildLocation()
        Map pg1 = [offset: TestUtils.randIntegerUpTo(88),
            max: TestUtils.randIntegerUpTo(88),
            total: TestUtils.randIntegerUpTo(88)]
        Map links = [(TestUtils.randString()): TestUtils.randString()]
        TypeMap params = TestUtils.randTypeMap()

        MockedMethod buildPagination = MockedMethod.create(ControllerUtils, "buildPagination") {
            pg1
        }
        MockedMethod buildLinks = MockedMethod.create(ControllerUtils, "buildLinks") {
            links
        }
        int countTimes = 0
        Closure doCount = { ++countTimes; numTotal; }
        List listArgs1 = []
        Closure doList1 = { arg1 -> listArgs1 << arg1; null; }
        List listArgs2 = []
        Closure doList2 = { arg1 -> listArgs2 << arg1; [loc1]; }

        when: "none found"
        controller.respondWithClosures(doCount, doList1, params, MarshallerUtils.KEY_LOCATION)

        then:
        countTimes == 1
        buildPagination.latestArgs == [params, numTotal]
        buildLinks.latestArgs[0].resource == controller.controllerName
        buildLinks.latestArgs[0].action == RestUtils.ACTION_GET_LIST
        buildLinks.latestArgs[0].absolute == false
        buildLinks.latestArgs[1] == pg1.offset
        buildLinks.latestArgs[2] == pg1.max
        buildLinks.latestArgs[3] == pg1.total
        listArgs1 == [pg1]
        response.status == ResultStatus.OK.intStatus
        response.json[MarshallerUtils.resolveCodeToPlural(MarshallerUtils.KEY_LOCATION)] == []
        response.json[MarshallerUtils.PARAM_LINKS] == links
        response.json[MarshallerUtils.PARAM_META] == pg1

        when: "some found"
        response.reset()
        controller.respondWithClosures(doCount, doList2, params, MarshallerUtils.KEY_LOCATION)

        then:
        countTimes == 2
        buildPagination.latestArgs == [params, numTotal]
        buildLinks.latestArgs[0].resource == controller.controllerName
        buildLinks.latestArgs[0].action == RestUtils.ACTION_GET_LIST
        buildLinks.latestArgs[0].absolute == false
        buildLinks.latestArgs[1] == pg1.offset
        buildLinks.latestArgs[2] == pg1.max
        buildLinks.latestArgs[3] == pg1.total
        listArgs2 == [pg1]
        response.status == ResultStatus.OK.intStatus

        and: "custom marshallers not initialized in unit tests"
        response.json instanceof Collection
        response.json[0].id == loc1.id

        cleanup:
        buildPagination?.restore()
        buildLinks?.restore()
    }

    void "test responding with criteria"() {
        given:
        String marshallerKey = TestUtils.randString()
        TypeMap params = TestUtils.randTypeMap()
        Closure sortOptions = { }

        DetachedCriteria criteria = GroovyMock() { asBoolean() >> true }
        MockedMethod respondWithClosures = MockedMethod.create(controller, "respondWithClosures")

        when:
        controller.respondWithCriteria(criteria, params, sortOptions, marshallerKey)

        then:
        respondWithClosures.latestArgs[0] instanceof Closure
        respondWithClosures.latestArgs[1] instanceof Closure
        respondWithClosures.latestArgs[2] == params
        respondWithClosures.latestArgs[3] == marshallerKey

        when:
        respondWithClosures.latestArgs[0].call()
        respondWithClosures.latestArgs[1].call()

        then:
        1 * criteria.count()
        1 * criteria.build(sortOptions) >> criteria
        1 * criteria.list(*_)

        cleanup:
        respondWithClosures?.restore()
    }
}
