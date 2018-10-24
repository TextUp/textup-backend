package org.textup.job

import grails.compiler.GrailsTypeChecked
import java.util.concurrent.TimeUnit
import org.quartz.Job
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

@GrailsTypeChecked
class FutureMessageJob implements Job {

	@Autowired
	FutureMessageJobService futureMessageJobService

    @Autowired
    ResultFactory resultFactory

    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    void execute(JobExecutionContext context) {
        // first wait for 8 seconds to allow for the future message to be saved
        // if we are firing it immediately so we are able to find the future message
        // when we are executing in the futureMessageJobService. We extended this sleep time from
        // 2 seconds to 8 seconds because media processing makes creating the future message take
        // longer than it used to
        TimeUnit.SECONDS.sleep(8)
        // after sleeping, then continue with execution
    	JobDataMap data = context.mergedJobDataMap
        String futureKey = Helpers.to(String, data.get(Constants.JOB_DATA_FUTURE_MESSAGE_KEY))
        futureMessageJobService
        	.execute(futureKey, Helpers.to(Long, data.get(Constants.JOB_DATA_STAFF_ID)))
            .logFail("FutureMessageJob.executing job")
        // try to mark done regardless of the execution succeeds or fails
        if (!context.trigger.mayFireAgain()) { // is last fire
            futureMessageJobService.markDone(futureKey)
                .logFail("FutureMessageJob: marking done")
        }
    }
}

