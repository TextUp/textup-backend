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
    	job = new FutureMessageJob()
    	job.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    void "test executing then mark done"() {
        given:
        job.threadService = Mock(ThreadService)
        job.futureMessageJobService = Mock(FutureMessageJobService)
        JobExecutionContext context = Mock()
        Trigger trig = Mock()
        String key = TestUtils.randString()
        Long id = 88
        Map data = [
            (QuartzUtils.DATA_FUTURE_MESSAGE_KEY): key,
            (QuartzUtils.DATA_STAFF_ID): id
        ]

    	when: "execution does not succeed but will not fire again"
        job.execute(context)

    	then: "mark done regardless of execution success"
        1 * context.mergedJobDataMap >> new JobDataMap(data)
        1 * context.trigger >> trig
        1 * trig.mayFireAgain() >> false
        1 * job.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * job.futureMessageJobService.execute(key, id) >>
            new Result(status: ResultStatus.INTERNAL_SERVER_ERROR).toGroup()
        1 * job.futureMessageJobService.markDone(key) >> new Result()

    	when: "execution succeeds but trigger may still fire again"
    	job.execute(context)

    	then: "don't mark done"
    	1 * context.mergedJobDataMap >> new JobDataMap(data)
        1 * context.trigger >> trig
        1 * trig.mayFireAgain() >> true
        1 * job.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * job.futureMessageJobService.execute(key, id) >> new ResultGroup()
        0 * job.futureMessageJobService.markDone(*_)

    	when: "execution succeeds and trigger will not fire again"
    	job.execute(context)

    	then: "mark done"
    	1 * context.mergedJobDataMap >> new JobDataMap(data)
        1 * context.trigger >> trig
        1 * trig.mayFireAgain() >> false
        1 * job.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * job.futureMessageJobService.execute(key, id) >> new ResultGroup()
        1 * job.futureMessageJobService.markDone(key) >> new Result()
    }
}
