package org.textup.jobs

import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

class FutureMessageJob {

	@Autowired
	FutureMessageService futureMessageService

    String group = Key.DEFAULT_GROUP

    void execute(JobExecutionContext context) {
    	JobDataMap data = context.mergedJobDataMap

        println "FutureMessageJob.execute: data: ${data}"
        println "\t context.trigger.mayFireAgain(): ${context.trigger.mayFireAgain()}"

        String futureKey = Helpers.toString(data.get(Constants.JOB_DATA_FUTURE_MESSAGE_KEY))
        futureMessageService
        	.execute(futureKey, Helpers.toLong(data.get(Constants.JOB_DATA_STAFF_ID)))
            .any({ Collection successes ->
                    if (!context.trigger.mayFireAgain()) { // is last fire

                        println("MARKING AS DONE!!")

                        futureMessageService.markDone(futureKey)
                    }
                    else { resultFactory.success() }
                })
        	.logFail("FutureMessageJob")
    }
}

