package org.textup.job

import grails.compiler.GrailsTypeChecked
import org.quartz.*
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

class DigestNotificationJob implements Job {

    static final String FREQ_KEY = "frequency"

    // Any additional properties provided here call analogous setters on CronTriggerImpl
    // see: https://github.com/grails-plugins/grails-quartz/blob/1.x/src/groovy/grails/plugins/quartz/config/TriggersConfigBuilder.groovy
    static triggers = {
        cron cronExpression: "0 0/15 * * * ?", // in UTC time, run every hour on each 15 min increment
            misfireInstruction: CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING,
            jobDataMap: new JobDataMap((FREQ_KEY): NotificationFrequency.QUARTER_HOUR.toString())
        cron cronExpression: "0 30 * * * ?", // in UTC time, run every hour on the half hour
            misfireInstruction: CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING,
            jobDataMap: new JobDataMap((FREQ_KEY): NotificationFrequency.HALF_HOUR.toString())
        cron cronExpression: "0 0 * * * ?", // in UTC time, run every hour on the hour
            misfireInstruction: CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING,
            jobDataMap: new JobDataMap((FREQ_KEY): NotificationFrequency.HOUR.toString())
    }

    // `requestsRecovery` default is false so don't send duplicate notifications when recovering
    // see: https://github.com/grails-plugins/grails-quartz/blob/1.x/src/java/grails/plugins/quartz/GrailsJobClassConstants.java
    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    @Autowired
    NotificationService notificationService

    @GrailsTypeChecked
    void execute(JobExecutionContext context) {
        NotificationFrequency freq1 = TypeMap.create(context.mergedJobDataMap)
            .enum(NotificationFrequency, FREQ_KEY)
        if (freq1) {
            Collection<RecordItem> rItems = RecordItems
                .buildForOutgoingScheduledOrIncomingMessagesAfter(freq1.buildDateTimeInPast())
                .list()
            NotificationUtils.tryBuildNotificationGroup(rItems)
                .then { NotificationGroup notifGroup ->
                    notificationService.send(notifGroup, freq1)
                }
                .logFail("sending digest notifications for frequency $freq1")
        }
        else { log.error("could not find stored frequency to start job") }
    }
}
