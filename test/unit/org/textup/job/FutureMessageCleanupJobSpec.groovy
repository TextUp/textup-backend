package org.textup.job

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.FutureMessageType
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt, FutureMessage])
@TestMixin(HibernateTestMixin)
class FutureMessageCleanupJobSpec extends Specification {

    void "test cleaning up completed messages not marked as such"() {
        given: "a future message not properly marked as done"
        Record rec1 = new Record()
        rec1.save(flush:true, failOnError:true)
        // started today or earlier and is NOT done
        FutureMessage fm1 = new FutureMessage(type:FutureMessageType.TEXT, message:"hi",
            record:rec1, startDate: DateTime.now().minusDays(10), isDone:false)
        fm1.metaClass.refreshTrigger = { -> }
        fm1.save(flush:true, failOnError:true)

        when: "executing this job"
        // isReallyDone will return true because the futureMessage's trigger
        // will be null because we've overridden `refreshTrigger` to avoid
        // setting the `trigger` property of the future message
        FutureMessageCleanupJob job1 = new FutureMessageCleanupJob()
        job1.execute()

        then: "future message is not properly marked as done"
        FutureMessage.get(fm1.id).isDone == true
    }
}
