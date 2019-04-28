package org.textup.util

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([Location, Record])
@TestMixin(HibernateTestMixin)
class DomainUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test try getting id"() {
        given:
        Location loc1 = TestUtils.buildLocation()

        expect: "has id"
        DomainUtils.tryGetId([]) == null

        and: "does not have id"
        DomainUtils.tryGetId(loc1) != null
        DomainUtils.tryGetId(loc1) == loc1.id
    }

    void "test getting instance properties"() {
        given:
        Author author1 = TestUtils.buildAuthor()

        when:
        Map rawProps = author1.properties
        Map cleanedProps = DomainUtils.instanceProps(author1)

        then:
        DomainUtils.INSTANCE_PROPS_TO_IGNORE.every { it in rawProps.keySet() }
        DomainUtils.INSTANCE_PROPS_TO_IGNORE.every { !(it in cleanedProps.keySet()) }
    }

    void "test checking for dirty non-object fields"() {
        when:
        Location loc1 = TestUtils.buildLocation()
        Location.withSession { it.flush() }

        then:
        DomainUtils.hasDirtyNonObjectFields(loc1) == false

        when:
        loc1.address = TestUtils.randString()

        then:
        DomainUtils.hasDirtyNonObjectFields(loc1) == true

        and: "is not dirty when `address` is ignored"
        DomainUtils.hasDirtyNonObjectFields(loc1, ["address"]) == false
    }

    void "test trying to save"() {
        given:
        Location invalidObj = new Location()
        Record validObj = new Record()

        when: "null"
        Result res = DomainUtils.trySave(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages == ["domainUtils.invalidInput"]

        when: "invalid"
        res = DomainUtils.trySave(invalidObj)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "valid"
        res = DomainUtils.trySave(validObj, ResultStatus.CREATED)

        then:
        res.status == ResultStatus.CREATED
        res.payload == validObj
    }

    void "test trying to save multiple"() {
        given:
        Location invalidObj = new Location()
        Record validObj = new Record()

        when: "null"
        Result res = DomainUtils.trySaveAll(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages == ["domainUtils.invalidInput"]

        when: "non-null but empty"
        res = DomainUtils.trySaveAll([])

        then:
        res.status == ResultStatus.NO_CONTENT
        res.payload == null

        when: "some invalid"
        res = DomainUtils.trySaveAll([validObj, invalidObj])

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.isEmpty() == false

        when: "all valid"
        res = DomainUtils.trySaveAll([validObj, validObj])

        then:
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
    }

    void "test trying to validate"() {
        given:
        Location invalidObj = new Location()
        Record validObj = new Record()

        when: "null"
        Result res = DomainUtils.tryValidate(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages == ["domainUtils.invalidInput"]

        when: "invalid"
        res = DomainUtils.tryValidate(invalidObj)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.isEmpty() == false

        when: "valid"
        res = DomainUtils.tryValidate(validObj, ResultStatus.CREATED)

        then:
        res.status == ResultStatus.CREATED
        res.payload == validObj
    }

    void "test trying to validate multiple"() {
        given:
        Location invalidObj = new Location()
        Record validObj = new Record()

        when: "null"
        Result res = DomainUtils.tryValidateAll(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages == ["domainUtils.invalidInput"]

        when: "non-null but empty"
        res = DomainUtils.tryValidateAll([])

        then:
        res.status == ResultStatus.NO_CONTENT
        res.payload == null

        when: "some invalid"
        res = DomainUtils.tryValidateAll([validObj, invalidObj])

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.isEmpty() == false

        when: "all valid"
        res = DomainUtils.tryValidateAll([validObj, validObj])

        then:
        res.status == ResultStatus.NO_CONTENT
        res.payload == null
    }
}
