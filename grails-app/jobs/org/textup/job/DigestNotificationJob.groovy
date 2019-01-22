package org.textup.job

import grails.compiler.GrailsTypeChecked
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

class DigestNotificationJob implements Job {

    private static final String FREQ_KEY = "frequency"

    // Any additional properties provided here call analogous setters on CronTriggerImpl
    // see: https://github.com/grails-plugins/grails-quartz/blob/1.x/src/groovy/grails/plugins/quartz/config/TriggersConfigBuilder.groovy
    static triggers = {
        cron cronExpression: "0 0/15 * * * ?", // in UTC time, run every hour on each 15 min increment
            misfireInstruction: CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING.
            jobDataMap: new JobDataMap((FREQ_KEY): NotificationFrequency.QUARTER_HOUR)
        cron cronExpression: "0 30 * * * ?", // in UTC time, run every hour on the half hour
            misfireInstruction: CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING.
            jobDataMap: new JobDataMap((FREQ_KEY): NotificationFrequency.HALF_HOUR)
        cron cronExpression: "0 0 * * * ?", // in UTC time, run every hour on the hour
            misfireInstruction: CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING.
            jobDataMap: new JobDataMap((FREQ_KEY): NotificationFrequency.HOUR)
    }

    // `requestsRecovery` default is false so don't send duplicate notifications when recovering
    // see: https://github.com/grails-plugins/grails-quartz/blob/1.x/src/java/grails/plugins/quartz/GrailsJobClassConstants.java
    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    @Autowired
    NotificationService notificationService

    @GrailsTypeChecked
    void execute(JobExecutionContext context) {

        // TODO remove after debugging
        println context.trigger.misfireInstruction
        println "CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING: ${CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING}"
        println "CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW : ${CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW }"


        NotificationFrequency freq1 = context.jobDataMap.get(FREQ_KEY) as NotificationFrequency
        if (freq1) {
            Collection<RecordItem> rItems = RecordItems
                .buildIncomingMessagesAfter(freq1.buildDateTimeInPast())
                .list()
            NotificationUtils.tryBuildNotificationGroup(rItems)
                .then { NotificationGroup notifGroup ->
                    notificationService.send(freq1, notifGroup)
                }
                .logFail("sending digest notifications for frequency $freq1")
        }
        else { log.error("Could not find stored frequency") }
    }
}
