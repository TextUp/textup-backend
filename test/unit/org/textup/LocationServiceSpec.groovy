package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain(Location)
@TestMixin(HibernateTestMixin)
@TestFor(LocationService)
class LocationServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        TypeMap body = TypeMap.create(address: TestUtils.randString(),
            lat: TestUtils.randIntegerUpTo(88),
            lng: TestUtils.randIntegerUpTo(88))

        when:
        Result res = service.tryCreate(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = service.tryCreate(body)

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof Location
        res.payload.address == body.address
        res.payload.lat == body.lat
        res.payload.lng == body.lng
    }

    void "test updating"() {
        given:
        Location loc1 = TestUtils.buildLocation()
        TypeMap body1 = TypeMap.create(address: TestUtils.randString())
        TypeMap body2 = TypeMap.create(address: TestUtils.randString(),
            lat: TestUtils.randIntegerUpTo(88),
            lng: TestUtils.randIntegerUpTo(88))

        when:
        Result res = service.tryUpdate(null, null)

        then:
        res.status == ResultStatus.OK

        when:
        res = service.tryUpdate(loc1, body1)

        then:
        res.status == ResultStatus.OK
        loc1.address == body1.address

        when:
        res = service.tryUpdate(loc1, body2)

        then:
        res.status == ResultStatus.OK
        loc1.address == body2.address
        loc1.lat == body2.lat
        loc1.lng == body2.lng
    }

    void "try creating or updating only if the location map is present"() {
        given:
        TypeMap lInfo = TestUtils.randTypeMap()
        TypeMap emptyInfo = TypeMap.create()
        Location loc1 = TestUtils.buildLocation()

        MockedMethod tryCreate = MockedMethod.create(service, "tryCreate") { Result.void() }
        MockedMethod tryUpdate = MockedMethod.create(service, "tryUpdate") { Result.void() }

        when:
        Result res = service.tryCreateOrUpdateIfPresent(null, emptyInfo)

        then:
        res.status == ResultStatus.OK
        tryCreate.notCalled
        tryUpdate.notCalled

        when:
        res = service.tryCreateOrUpdateIfPresent(null, lInfo)

        then:
        res.status == ResultStatus.NO_CONTENT
        tryCreate.latestArgs == [lInfo]
        tryUpdate.notCalled

        when:
        res = service.tryCreateOrUpdateIfPresent(loc1, lInfo)

        then:
        res.status == ResultStatus.NO_CONTENT
        tryCreate.callCount == 1
        tryUpdate.latestArgs == [loc1, lInfo]

        cleanup:
        tryCreate?.restore()
        tryUpdate?.restore()
    }
}
