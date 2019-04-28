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

@TestFor(ScheduleService)
class ScheduleServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test updating"() {
        given:
        String tz = TestUtils.randString()
        Schedule sched1 = GroovyMock() { asBoolean() >> true }
        TypeMap body = TypeMap.create(manual: true, manualIsAvailable: true)

        when:
        Result res = service.tryUpdate(null, null, null)

        then:
        notThrown NullPointerException
        res.status == ResultStatus.OK
        res.payload == null

        when:
        res = service.tryUpdate(sched1, body, tz)

        then:
        1 * sched1.with(_ as Closure) >> { args ->
            args[0].setDelegate(sched1)
            args[0].call()
        }
        1 * sched1.setManual(true)
        1 * sched1.setManualIsAvailable(true)
        1 * sched1.save() >> sched1
        1 * sched1.updateWithIntervalStrings(body, tz) >> Result.createSuccess(sched1)
        res.status == ResultStatus.OK
    }
}
