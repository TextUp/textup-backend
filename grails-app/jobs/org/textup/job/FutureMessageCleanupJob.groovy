package org.textup.job

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

class FutureMessageCleanupJob implements Job {
    static triggers = {
      cron cronExpression: "0 0 5 * * ?" // note this is in UTC time
    }

    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    @GrailsTypeChecked
    void execute(JobExecutionContext context = null) {
        DateTime now = DateTimeUtils.now()
        // examine messages that are NOT done but have started either today or earlier
        DetachedCriteria query = FutureMessage.where {
            isDone == false && startDate <= DateTime.now()
        }
        Collection<FutureMessage> toBeCheckedList = query.list()
        Collection<FutureMessage> withErrorMsgs = []
        // get all messages our db records show as NOT done
        toBeCheckedList.each { FutureMessage fMsg1 ->
            // for those that are marked as NOT done but ARE actually done
            if (fMsg1.isReallyDone) {
                // update our db records to make these as done
                fMsg1.isDone = true
                if (!fMsg1.save()) { withErrorMsgs << fMsg1 }
            }
        }
        log.info("FutureMessageCleanupJob: checked ${toBeCheckedList.size()} future messages")
        if (withErrorMsgs) {
            log.error """
                FutureMessageCleanupJob: could not mark following scheduled message ids as
                done ${withErrorMsgs*.id}. Validation errors are: ${withErrorMsgs*.errors}
            """
        }
    }
}
