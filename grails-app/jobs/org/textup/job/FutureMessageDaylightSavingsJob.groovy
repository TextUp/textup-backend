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
    void execute(JobExecutionContext context) {

        DateTime now = DateTime.now(DateTimeZone.UTC)
        // query future messages NOT ADJUSTED YET that are scheduled for adjustment EITHER
        // (1) today at either the current hour or at a previous hour OR
        // (2) at any hour yesterday
        // We include previous times here for redundancy in case of a prior job error.
        DetachedCriteria query = FutureMessage.where {
            isDone == false && hasAdjustedDaylightSavings == false &&
                year(whenAdjustDaylightSavings) == now.getYear() &&
                month(whenAdjustDaylightSavings) == now.getMonthOfYear() &&
                (
                    (day(whenAdjustDaylightSavings) == now.getDayOfMonth() &&
                        hour(whenAdjustDaylightSavings) <= now.getHourOfDay()) ||
                    (day(whenAdjustDaylightSavings) == now.getDayOfMonth() - 1)
                )

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
