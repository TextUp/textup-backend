package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.json.JsonBuilder
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.textup.*
import org.textup.type.OrgStatus
import org.textup.util.CustomSpec
import org.textup.util.PusherTester
import org.textup.validator.*
import spock.lang.Shared
import static javax.servlet.http.HttpServletResponse.*

@TestFor(BaseController)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class BaseControllerSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        // enables resolving of resource names from class
        controller.grailsApplication.flatConfig = config.flatten()
    }
    def cleanup() {
        cleanupData()
    }

    void "test validating json requests"() {
        when: "invalid json without specified class"
        request.json = "{'invaild:{}}"
        boolean outcome = controller.validateJsonRequest(request)

        then: "bad request"
        outcome == false
        response.status == SC_BAD_REQUEST
    }

    void "test validating json requests"() {
        when: "valid json without specified class"
        request.json = "{'valid':{}}"
        boolean outcome = controller.validateJsonRequest(request)

        then:
        outcome == true
        response.status == SC_OK
    }

    void "test validating json requests"() {
        when: "invalid json with class specified"
        request.json = "{'contact':{'invalid:123}}"
        boolean outcome = controller.validateJsonRequest(Contact, request)

        then: "bad request"
        outcome == false
        response.status == SC_BAD_REQUEST
    }

    void "test validating json requests"() {
        when: "valid json with class specified"
        request.json = "{'contact':{'valid':123}}"
        boolean outcome = controller.validateJsonRequest(Contact, request)

        then:
        outcome == true
        response.status == SC_OK
    }

    void "test building errors"() {
        given: "error results"
        ResultFactory fac = getResultFactory()
        String errorCode = "I am an a valid error code"
        Collection<Result<?>> manyErrorRes = []
        Location loc1 = new Location()
        assert loc1.validate() == false
        addToMessageSource(errorCode)
        manyErrorRes << fac.failWithCodeAndStatus(errorCode, ResultStatus.BAD_REQUEST)
        manyErrorRes << fac.failWithValidationErrors(loc1.errors)

        expect:
        controller.buildErrorObj(manyErrorRes).each { Map<String,Object> errorObj ->
            assert errorObj.message instanceof String
            assert errorObj.message != ""
            assert errorObj.statusCode instanceof Number
            assert errorObj.statusCode >= 400
        }
    }

    void "test resolving class as string"() {
        when: "we have classes that are resolvable"
        Collection<Class> resolvableClasses = [AvailablePhoneNumber, Contactable, ContactTag,
            FeaturedAnnouncement, FutureMessage, ImageInfo, IncomingSession, Location, MergeGroup,
            Notification, NotificationStatus, Organization, Phone, RecordItem, RecordItemReceipt,
            RecordNoteRevision, Schedule, Staff, Team]

        then:
        resolvableClasses.each { Class clazz ->
            assert controller.resolveClassToConfigKey(clazz) != "result"
            assert controller.resolveClassToSingular(clazz) != Constants.FALLBACK_SINGULAR
            assert controller.resolveClassToPlural(clazz) != Constants.FALLBACK_PLURAL
        }

        expect: "graceful fallback for classes that are not resolvable"
        controller.resolveClassToConfigKey(Role) == "result"
        controller.resolveClassToSingular(Role) == Constants.FALLBACK_SINGULAR
        controller.resolveClassToPlural(Role) == Constants.FALLBACK_PLURAL
    }

    void "test resolving resource names"() {
        when: "we have classes that have resource names"
        Collection<Class> resolvableClasses = [AvailablePhoneNumber, Contactable, ContactTag,
            FeaturedAnnouncement, FutureMessage, IncomingSession, Notification, Organization,
            RecordItem, Staff, Team]

        then:
        resolvableClasses.each { Class clazz ->
            assert controller.resolveClassToResourceName(clazz) != Constants.FALLBACK_RESOURCE_NAME
        }

        expect: "graceful fallback for classes that do not associated resource names"
        controller.resolveClassToConfigKey(Role) == "result"
    }

    void "test building pagination options"() {
        given:
        Integer defaultMax = config.textup.defaultMax
        Integer largestMax = config.textup.largestMax

        expect:
        controller.buildPaginationOptions(null, null, null) == [defaultMax, 0, defaultMax]
        controller.buildPaginationOptions(1, null, null) == [1, 0, 1]
        controller.buildPaginationOptions(-1, null, null) == [defaultMax, 0, defaultMax]
        controller.buildPaginationOptions(null, -1, null) == [defaultMax, 0, defaultMax]
        controller.buildPaginationOptions(null, null, -1) == [defaultMax, 0, defaultMax]
        controller.buildPaginationOptions(10, 0, 10) == [10, 0, 10]
        controller.buildPaginationOptions(20, 0, 10) == [20, 0, 10]
        controller.buildPaginationOptions(20, 30, 10) == [20, 30, 10]
        controller.buildPaginationOptions(largestMax + 1, 0, 0) == [largestMax, 0, 0]
        controller.buildPaginationOptions(null, -1, largestMax + 1) == [defaultMax, 0, largestMax + 1]
        controller.buildPaginationOptions(0, 100, 0) == [defaultMax, 100, 0]
    }

    void "test pagination"() {
        expect:
        testPagination(10, 0, 10, [hasNext:false , hasPrev:false])
        testPagination(10, 10, 0, [hasNext:false , hasPrev:true])
        testPagination(10, 0, 11, [hasNext:true , hasPrev:false])
        testPagination(10, 10, 21, [hasNext:true , hasPrev:true])
    }

    void "test responding with count and list closures"() {
        when: "none found"
        controller.respondWithMany(Contact, { 0 }, { [] })

        then:
        response.status == SC_OK
        response.json.contacts instanceof List
        response.json.contacts.isEmpty() == true

        when: "none returned, but many total"
        response.reset()
        controller.respondWithMany(Contact, { 100 }, { [] })

        then: "should have valid next link"
        response.status == SC_OK
        response.json.contacts instanceof List
        response.json.contacts.isEmpty() == true
        response.json.links instanceof Map
        response.json.links.next.contains("/v1/contacts")

        when: "some found"
        response.reset()
        controller.respondWithMany(Contact, { 10 }, { [c1, c2] })

        then:
        response.status == SC_OK
        response.json.contacts instanceof List
        // array is not empty but marshallers are not provisioned in unit
        // tests so the array contains nulls
        response.json.contacts.isEmpty() == false
    }

    void "test responding with a result"() {
        when: "result failure"
        String errorMsg = "error message here"
        controller.respondWithResult(Contact, Result.<Contact>createError([errorMsg],
            ResultStatus.BAD_REQUEST))

        then:
        response.status == SC_BAD_REQUEST
        response.json.errors instanceof Collection
        response.json.errors.size() == 1
        response.json.errors[0] instanceof Map
        response.json.errors[0].message == errorMsg
        response.json.errors[0].statusCode == SC_BAD_REQUEST

        when: "payload is Void"
        response.reset()
        controller.respondWithResult(Void, Result.<Void>createSuccess(null, ResultStatus.OK))

        then:
        response.status == SC_OK
        response.text == ""

        when: "status is NO_CONTENT"
        response.reset()
        controller.respondWithResult(Contact, Result.<Contact>createSuccess(null,
            ResultStatus.NO_CONTENT))

        then:
        response.status == SC_NO_CONTENT
        response.text == ""

        when: "payload is Closure"
        response.reset()
        controller.respondWithResult(Closure, Result.<Closure>createSuccess({ Response {} },
            ResultStatus.OK))

        then:
        response.status == SC_OK
        response.json.isEmpty()
        response.xml != null

        when: "success and not previously mentioned special condition"
        response.reset()
        controller.respondWithResult(Contact, Result.<Contact>createSuccess(c1, ResultStatus.CREATED))

        then:
        response.status == SC_CREATED
        // marshaller not provisioned so we don't have the namespaced json
        // instead using the built-in json marshaller
        response.json?.id == c1.id
    }

    void "test responding with a result group"() {
        given: "success and failure results"
        String errStr1 = "I am an error"
        String errStr2 = "something is wrong"
        String errStr3 = "oops"
        Result<Contact> succ1 = Result.<Contact>createSuccess(c1, ResultStatus.OK)
        Result<Contact> succ2 = Result.<Contact>createSuccess(c2, ResultStatus.OK)
        Result<Contact> fail1 = Result.<Contact>createError([errStr1], ResultStatus.BAD_REQUEST)
        Result<Contact> fail2 = Result.<Contact>createError([errStr2], ResultStatus.UNPROCESSABLE_ENTITY)
        Result<Contact> fail3 = Result.<Contact>createError([errStr3], ResultStatus.UNPROCESSABLE_ENTITY)

        Collection<String> errMsgs = [errStr1, errStr2, errStr3]
        Collection<Long> cIds = [c1, c2]*.id

        when: "an empty group of results"
        controller.respondWithResult(Contact, new ResultGroup<Contact>())

        then:
        response.status == SC_INTERNAL_SERVER_ERROR
        response.text == ""

        when: "group with only failures"
        response.reset()
        controller.respondWithResult(Contact, new ResultGroup<Contact>([fail1, fail2, fail3]))

        then:
        response.status == 422 // UNPROCESSABLE_ENTITY
        response.json.errors.size() == 3
        response.json.errors.each {
            assert it.message in errMsgs
            assert it.statusCode in [SC_BAD_REQUEST, 422]
        }

        when: "group with only successes"
        response.reset()
        controller.respondWithResult(Contact, new ResultGroup<Contact>([succ1, succ2]))

        then:
        response.status == SC_OK
        response.json.size() == 2
        response.json*.id.every { (it as Long) in cIds }

        when: "group with both failures and successes"
        response.reset()
        controller.respondWithResult(Contact, new ResultGroup<Contact>([succ1, succ2, fail1, fail2, fail3]))

        then:
        response.status == SC_OK
        response.json.size() == 2
        // errors object not included because we using the standard rather
        // than the custom marshallers
        response.json*.id.every { (it as Long) in cIds }
    }

    void "test status rendering helpers"() {
        when:
        controller.ok()

        then:
        response.status == SC_OK

        when:
        controller.notFound()

        then:
        response.status == SC_NOT_FOUND

        when:
        controller.forbidden()

        then:
        response.status == SC_FORBIDDEN

        when:
        controller.unauthorized()

        then:
        response.status == SC_UNAUTHORIZED

        when:
        controller.notAllowed()

        then:
        response.status == SC_METHOD_NOT_ALLOWED

        when:
        controller.failsValidation()

        then:
        response.status == 422 // UNPROCESSABLE_ENTITY (not included in static codes)

        when:
        controller.badRequest()

        then:
        response.status == SC_BAD_REQUEST

        when:
        controller.noContent()

        then:
        response.status == SC_NO_CONTENT

        when:
        controller.error()

        then:
        response.status == SC_INTERNAL_SERVER_ERROR
    }

    // Helpers
    // -------

    protected void testPagination(Integer optMax, Integer optOffset, Integer optTotal,
        Map<String,Boolean> outcomes)  {

        Map<String,? extends Object> paginationOutput = controller.handlePagination(optMax, optOffset, optTotal)
        assert (paginationOutput.links?.next != null) == outcomes.hasNext
        assert (paginationOutput.links?.prev != null) == outcomes.hasPrev
        assert paginationOutput.meta != null
    }
}
