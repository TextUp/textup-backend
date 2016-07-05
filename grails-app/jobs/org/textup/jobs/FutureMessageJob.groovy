package org.textup.jobs

import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

class FutureMessageJob {

	@Autowired
	FutureMessageService futureMessageService

	String fullName = Constants.JOB_FUTURE_MESSAGE

    void execute(JobExecutionContext context) {
    	JobDataMap data = context.mergedJobDataMap
        futureMessageService
        	.execute(Helpers.toString(data.get(Constants.JOB_DATA_FUTURE_MESSAGE_KEY)),
        		Helpers.toLong(data.get(Constants.JOB_DATA_STAFF_ID)))
        	.logFail("FutureMessageJob")
    }
}

