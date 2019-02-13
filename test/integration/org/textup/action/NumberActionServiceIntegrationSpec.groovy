package org.textup.action

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
import org.textup.validator.action.*
import spock.lang.*

// This is an integration test because unit testing mocking addTo* and removeFrom* methods for
// Grails-managed hasMany relationships is very unreliable

class NumberActionServiceIntegrationSpec extends Specification {

    NumberActionService numberActionService

    void "test has actions"() {
        expect:
        numberActionService.hasActions(doNumberActions: null) == false
        numberActionService.hasActions(doNumberActions: "ok")
    }

    void "test handling actions"() {
        given:
        String str1 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        Integer pref = 10

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE

        ContactNumberAction a1 = GroovyMock()
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1])
        }

        when:
        Result res = numberActionService.tryHandleActions(spr1.toWrapper(), [doNumberActions: str1])

        then:
        tryProcess.latestArgs == [ContactNumberAction, str1]
        res.status == ResultStatus.FORBIDDEN

        when:
        res = numberActionService.tryHandleActions(ipr1.toWrapper(), [doNumberActions: str1])

        then:
        tryProcess.latestArgs == [ContactNumberAction, str1]
        1 * a1.toString() >> ContactNumberAction.MERGE
        1 * a1.buildPhoneNumber() >> pNum1
        1 * a1.preference >> pref
        res.status == ResultStatus.OK
        res.payload == ipr1.toWrapper()
        ipr1.numbers.find { it.number == pNum1.number }

        when:
        res = numberActionService.tryHandleActions(ipr1.toWrapper(), [doNumberActions: str1])

        then:
        tryProcess.latestArgs == [ContactNumberAction, str1]
        1 * a1.toString() >> ContactNumberAction.DELETE
        1 * a1.buildPhoneNumber() >> pNum1
        res.status == ResultStatus.OK
        res.payload == ipr1.toWrapper()
        !ipr1.numbers.find { it.number == pNum1.number }

        cleanup:
        tryProcess?.restore()
    }
}
