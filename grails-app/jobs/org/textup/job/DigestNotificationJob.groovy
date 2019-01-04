package org.textup.job

import grails.compiler.GrailsTypeChecked
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

class DigestNotificationJob implements Job {
    static triggers = {
        // TODO pick one or the other
        cron cronExpression: "0 0 * * * ?" // in UTC time, run every hour on the hour
        // cron cronExpression: "0 30 * * * ?" // in UTC time, run every hour on the half hour
    }

    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    @Autowired
    NotificationService notificationService

    @GrailsTypeChecked
    void execute(JobExecutionContext context = null) {

    }
}
