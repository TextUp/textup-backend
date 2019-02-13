package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.*
import org.quartz.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class QuartzUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    Scheduler mockScheduler

    def setup() {
        TestUtils.standardMockSetup()
        mockScheduler = Mock(Scheduler)
        IOCUtils.metaClass."static".getQuartzScheduler = { -> mockScheduler }
    }

    void "test building trigger"() {
        given:
        TriggerKey trigKey = new TriggerKey(TestUtils.randString())
        Long authId = TestUtils.randIntegerUpTo(88)
        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") {
            Result.createSuccess(authId)
        }
        FutureMessage mockMsg = GroovyMock() {
            getKeyName() >> TestUtils.randString()
            getStartDate() >> DateTime.now()
        }
        Trigger existingTrigger = Mock()

        when: "new"
        Result res = QuartzUtils.tryBuildTrigger(mockMsg)

        then: "no existing as second in tuple"
        1 * mockMsg.triggerKey >> trigKey
        1 * mockScheduler.getTrigger(*_) >> null
        res.status == ResultStatus.OK
        res.payload instanceof Tuple
        res.payload.first != null
        res.payload.first.jobDataMap[QuartzUtils.DATA_FUTURE_MESSAGE_KEY] == mockMsg.keyName
        res.payload.first.jobDataMap[QuartzUtils.DATA_STAFF_ID] == authId
        res.payload.second == null // no existing

        when: "existing"
        res = QuartzUtils.tryBuildTrigger(mockMsg)

        then: "existing passed as second in tuple"
        1 * mockMsg.triggerKey >> trigKey
        1 * mockScheduler.getTrigger(*_) >> existingTrigger
        1 * existingTrigger.triggerBuilder >> TriggerBuilder.newTrigger()
        res.status == ResultStatus.OK
        res.payload instanceof Tuple
        res.payload.first != null
        res.payload.first.jobDataMap[QuartzUtils.DATA_FUTURE_MESSAGE_KEY] == mockMsg.keyName
        res.payload.first.jobDataMap[QuartzUtils.DATA_STAFF_ID] == authId
        res.payload.second != null

        cleanup:
        tryGetAuthId.restore()
    }
}
