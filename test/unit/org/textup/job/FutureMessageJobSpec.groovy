package org.textup.job

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.Trigger
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class FutureMessageJobSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

	FutureMessageJob job

    def setup() {
        TestUtils.standardMockSetup()
    	job = new FutureMessageJob()
    }

    void "test executing then mark done"() {
        given:
        String key = TestUtils.randString()
        Long id = TestUtils.randIntegerUpTo(88)
        Map data = [(QuartzUtils.DATA_FUTURE_MESSAGE_KEY): key, (QuartzUtils.DATA_STAFF_ID): id]

        JobExecutionContext context = GroovyMock()
        Trigger trig = GroovyMock()
        job.threadService = GroovyMock(ThreadService)
        job.futureMessageJobService = GroovyMock(FutureMessageJobService)

    	when: "execution does not succeed but will not fire again"
        job.execute(context)

    	then: "mark done regardless of execution success"
        1 * context.mergedJobDataMap >> new JobDataMap(data)
        1 * context.trigger >> trig
        1 * trig.mayFireAgain() >> false
        1 * job.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * job.futureMessageJobService.execute(key, id) >>
            Result.createError([], ResultStatus.INTERNAL_SERVER_ERROR)
        1 * job.futureMessageJobService.markDone(key) >> Result.void()

    	when: "execution succeeds but trigger may still fire again"
    	job.execute(context)

    	then: "don't mark done"
    	1 * context.mergedJobDataMap >> new JobDataMap(data)
        1 * context.trigger >> trig
        1 * trig.mayFireAgain() >> true
        1 * job.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * job.futureMessageJobService.execute(key, id) >> Result.void()
        0 * job.futureMessageJobService.markDone(*_)

    	when: "execution succeeds and trigger will not fire again"
    	job.execute(context)

    	then: "mark done"
    	1 * context.mergedJobDataMap >> new JobDataMap(data)
        1 * context.trigger >> trig
        1 * trig.mayFireAgain() >> false
        1 * job.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * job.futureMessageJobService.execute(key, id) >> Result.void()
        1 * job.futureMessageJobService.markDone(key) >> Result.void()
    }
}
