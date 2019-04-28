package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.quartz.*
import org.textup.*
import org.textup.job.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class QuartzUtils {

    static final String DATA_FUTURE_MESSAGE_KEY = "futureMessageKey"
    static final String DATA_STAFF_ID = "futureMessageStaffId"

    static Result<Tuple<Trigger, Trigger>> tryBuildTrigger(FutureMessage fMsg) {
        AuthUtils.tryGetAuthId()
            .then { Long authId ->
                TriggerKey trigKey = fMsg.triggerKey
                Trigger existing = IOCUtils.quartzScheduler.getTrigger(trigKey)
                TriggerBuilder builder = getBuilder(existing)
                builder
                    .forJob(FutureMessageJob.class.canonicalName)
                    .withIdentity(trigKey)
                    .startAt(fMsg.startDate?.toDate())
                    .usingJobData(QuartzUtils.DATA_FUTURE_MESSAGE_KEY, fMsg.keyName)
                    .usingJobData(QuartzUtils.DATA_STAFF_ID, authId)
                if (fMsg.endDate) {
                    builder.endAt(fMsg.endDate.toDate())
                }
                ScheduleBuilder sBuilder = fMsg.scheduleBuilder
                if (sBuilder) {
                    builder.withSchedule(sBuilder)
                }
                IOCUtils.resultFactory.success(builder.build(), existing)
            }
    }

    // Helpers
    // -------

    static TriggerBuilder getBuilder(Trigger existing) {
        existing ? existing.triggerBuilder : TriggerBuilder.newTrigger()
    }
}
