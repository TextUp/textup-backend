package org.textup.job

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.textup.FutureMessage

class FutureMessageDaylightSavingsJob implements Job {
    static triggers = {
      cron cronExpression: "0 0 * * * ?" // in UTC time, run every hour every day
    }

    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    @GrailsTypeChecked
    void execute(JobExecutionContext context = null) {
        // set datetime to the end of this current hour or very start of the next hour
        DateTime dateTime1 = DateTime
            .now(DateTimeZone.UTC)
            .withMinuteOfHour(0)
            .withSecondOfMinute(0)
            .plusHours(1)
        // query future messages NOT ADJUSTED YET that are scheduled for adjustment during this
        // current or anytime before. We include previous times here for redundancy in case
        // of a prior job error.
        DetachedCriteria query = FutureMessage.where {
            isDone == false && hasAdjustedDaylightSavings == false &&
                whenAdjustDaylightSavings < dateTime1
        }
        Collection<FutureMessage> toBeAdjustedList = query.list()
        Collection<FutureMessage> withErrorMsgs = []
        toBeAdjustedList.each { FutureMessage fMsg1 ->
            DateTimeZone zone1 = fMsg1.daylightSavingsZone
            if (zone1) {
                int createdOffset = Math.abs(zone1.getOffset(fMsg1.whenCreated)),
                    startOffset = Math.abs(zone1.getOffset(fMsg1.startDate))
                // adjust start date accordingly
                fMsg1.startDate = fMsg1.startDate.plusMillis(startOffset - createdOffset)
                fMsg1.hasAdjustedDaylightSavings = true
                if (!fMsg1.save()) { withErrorMsgs << fMsg1 }
            }
        }
        if (withErrorMsgs) {
            log.error """
                FutureMessageDaylightSavingsJob: could not adjust the following with ids
                ${withErrorMsgs*.id}. Validation errors are: ${withErrorMsgs*.errors}
            """
        }
    }
}
