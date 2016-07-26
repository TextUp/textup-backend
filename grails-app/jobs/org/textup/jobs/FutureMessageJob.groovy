package org.textup.jobs

import grails.compiler.GrailsTypeChecked
import java.util.concurrent.TimeUnit
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

@GrailsTypeChecked
class FutureMessageJob {

	@Autowired
	FutureMessageService futureMessageService

    @Autowired
    ResultFactory resultFactory

    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    void execute(JobExecutionContext context) {
        // first wait for 2 seconds to allow for the future message to be saved
        // if we are firing it immediately so we are able to find the future message
        // when we are executing in the futureMessageService
        TimeUnit.SECONDS.sleep(2)
        // after sleeping, then continue with execution
    	JobDataMap data = context.mergedJobDataMap
        String futureKey = Helpers.toString(data.get(Constants.JOB_DATA_FUTURE_MESSAGE_KEY))
        futureMessageService
        	.execute(futureKey, Helpers.toLong(data.get(Constants.JOB_DATA_STAFF_ID)))
            .logFail("FutureMessageJob.executing job")
        // try to mark done regardless of the execution succeeds or fails
        if (!context.trigger.mayFireAgain()) { // is last fire
            futureMessageService.markDone(futureKey)
                .logFail("FutureMessageJob: marking done")
        }
        else { resultFactory.success() }
    }
}

