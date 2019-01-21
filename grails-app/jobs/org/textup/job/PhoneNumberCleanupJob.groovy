package org.textup.job

import grails.compiler.GrailsTypeChecked
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.utils.Key
import org.springframework.beans.factory.annotation.Autowired
import org.textup.*

class PhoneNumberCleanupJob implements Job {
    static triggers = {
      cron cronExpression: "0 0 5 ? * SATL" // 5a UTC time the last Saturday of every month
    }

    boolean concurrent = false
    String group = Key.DEFAULT_GROUP

    @Autowired
    NumberService numberService

    @GrailsTypeChecked
    void execute(JobExecutionContext context = null) {
        numberService.cleanupInternalNumberPool()
            .logFail("PhoneNumberCleanupJob")
            .thenEnd { Tuple<Collection<String>, Collection<String>> outcome ->
                log.info("PhoneNumberCleanupJob: deleted numbers with ids: `${outcome.first}`, \
                    could not delete numbers with ids: `${outcome.second}`")
            }
    }
}
