package org.textup.jobs

import org.quartz.JobExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

class FutureMessageJob {

	@Autowired
	RecordService recordService

	String fullName = Constants.JOB_FUTURE_MESSAGE

    void execute(JobExecutionContext context) {
    	JobDataMap data = context.mergedJobDataMap
        recordService
        	.createForFuture(data.get(Constants.JOB_DATA_FUTURE_MESSAGE_KEY)?.toString())
        	.logFail("SendMessageJob")
    }
}

