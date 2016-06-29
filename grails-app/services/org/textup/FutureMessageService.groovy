package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.quartz.Scheduler
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import static org.springframework.http.HttpStatus.*

@Transactional
class FutureMessageService {

	AuthService authService
	ResultFactory resultFactory
	Scheduler quartzScheduler

	// Scheduler
	// ---------

	Result<DateTime> schedule() {
        try {
            TriggerKey trigKey = this.triggerKey
            Trigger trig = quartzScheduler.getTrigger(trigKey)
            TriggerBuilder builder = trig ? trig.triggerBuilder : TriggerBuilder.newTrigger()
            builder
                .forJob(Constants.JOB_FUTURE_MESSAGE)
                .withIdentity(trigKey)
                .startAt(this.startDate.toDate())
                .withSchedule(this.getSchedule())
                .usingJobData(Constants.JOB_DATA_FUTURE_MESSAGE_KEY, this.key)
            if (endDate) {
                builder.endAt(this.endDate.toDate())
            }
            Trigger newTrig = builder.build()
            // schedule or reschedule trigger
            Date nextFire = trig ? quartzScheduler.rescheduleJob(trigKey, newTrig) :
                quartzScheduler.scheduleJob(newTrig)
            if (nextFire) { // rescheduleJob can return null if unsuccessful
                resultFactory.success(new DateTime(nextTime))
            }
            else {
                resultFactory.failWithMessageAndStatus(INTERNAL_SERVER_ERROR,
                    "futureMessage.schedule.unspecifiedError")
            }
        }
        catch (Throwable e) { resultFactory.failWithThrowable(e) }
    }
    Result unschedule() {
        try {
            quartzScheduler.unscheduleJob(this.triggerKey) ?
                resultFactory.success() :
                resultFactory.failWithMessageAndStatus(INTERNAL_SERVER_ERROR,
                    "futureMessage.unschedule.unspecifiedError")
        }
        catch (Throwable e) { resultFactory.failWithThrowable(e) }
    }

    // Scheduler helper methods
    // ------------------------

    protected SimpleScheduleBuilder getSchedule() {
        SimpleScheduleBuilder builder = ScheduleBuilder.simpleSchedule()
        builder.withIntervalInMilliseconds(this.repeatIntervalInMillis)
        if (endDate) {
            builder.repeatForever()
        }
        else { builder.withRepeatCount(this.repeatCount) }
        builder
    }

	// Create
	// ------

    // Update
    // ------

    // Execute
    // -------

    ResultList<RecordItem> execute(String futureKey) {
        FutureMessage fMsg = FutureMessage.findByKey(futureKey)
        if (!fMsg) {
            return new ResultList(resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "recordService.execute.messageNotFound"))
        }
        Phone[] phones = authService.getPhonesForRecords([fMsg.record], false).toArray()
        if (!phones || !phones[0]) {
        	return new ResultList(resultFactory.failWithMessageAndStatus(NOT_FOUND,
        		"recordService.execute.phoneNotFound"))
        }
        phones[0]
    }
}
