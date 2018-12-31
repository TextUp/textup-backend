package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.context.support.StaticMessageSource
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class ResultFactorySpec extends Specification {

    ResultFactory resultFactory

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        resultFactory = new ResultFactory()
    }

    void "test success"() {
    	given:
    	Location loc1 = TestUtils.buildLocation()
        Location loc2 = TestUtils.buildLocation()

    	when: "without specifying payload"
    	Result<Location> res = resultFactory.<Location>success()

    	then:
    	res.success == true
    	res.status == ResultStatus.NO_CONTENT
    	res.payload == null
    	res.errorMessages.isEmpty() == true

    	when: "with specified payload"
    	res = resultFactory.<Location>success(loc1)

    	then:
    	res.success == true
    	res.errorMessages.isEmpty() == true
    	res.status == ResultStatus.OK
    	res.payload.id == loc1.id

        when: "specifying two payloads"
        Result<Tuple<Location, Location>> res2 = resultFactory.success(loc1, loc2)

        then:
        res2.status == ResultStatus.OK
        res2.payload instanceof Tuple
        res2.payload.first == loc1
        res2.payload.second == loc2
    }

    void "test fail for results and status and result group"() {
        given:
        ResultStatus stat1 = ResultStatus.UNPROCESSABLE_ENTITY
        Result<String> res1 = Result.createError(["1"], stat1)
        Result<String> res2 = Result.createSuccess("hi", ResultStatus.CREATED)
        Result<String> res3 = Result.createError(["3"], stat1)

        when: "results and status"
        Result<String> res = resultFactory.failWithResultsAndStatus([res1, res2, res3], stat1)

        then:
        res.payload == null
        res.status == stat1
        res.errorMessages.size() == 2 // one result was NOT an error

        when: "result group"
        ResultGroup<String> resGroup = new ResultGroup<>([res1, res2, res3])
        res = resultFactory.failWithGroup(resGroup)

        then:
        res.payload == null
        res.status == stat1
        res.errorMessages.size() == 2 // one result was NOT an error
    }

    void "test fail for code and status"() {
        given:
        String code = TestUtils.randString()
        ResultStatus stat1 = ResultStatus.LOCKED

        when:
        Result<String> res = resultFactory.failWithCodeAndStatus(code, stat1)

        then:
        res.payload == null
        res.status == stat1
        res.errorMessages.size() == 1
        res.errorMessages[0] == code
    }

    void "test fail for throwable"() {
        given:
        String msg = TestUtils.randString()

        when:
        Result res = resultFactory.failWithThrowable(new Throwable(msg))

        then:
        res.payload == null
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages.size() == 1
        res.errorMessages[0] == msg
    }

    void "test fail for validation errors"() {
        given:
        Location emptyLoc1 = new Location()
        Location emptyLoc2 = new Location()
        assert emptyLoc1.validate() == false
        assert emptyLoc2.validate() == false

        when: "single error object"
        Result res = resultFactory.failWithValidationErrors(emptyLoc1.errors)

        then:
        res.payload == null
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() > 0

        when: "many validation errors"
        res = resultFactory.failWithManyValidationErrors([emptyLoc1, emptyLoc2]*.errors)

        then:
        res.payload == null
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() > 0
    }
}
