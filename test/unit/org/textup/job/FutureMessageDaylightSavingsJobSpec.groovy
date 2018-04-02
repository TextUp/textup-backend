package org.textup.job

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.*
import org.textup.type.FutureMessageType
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt, FutureMessage])
@TestMixin(HibernateTestMixin)
class FutureMessageDaylightSavingsJobSpec extends Specification {

    void "test adjusting daylight savings time by hour"() {
        given: "a future message needing adjusting"
        Record rec1 = new Record()
        rec1.save(flush:true, failOnError:true)
        // started today or earlier and is NOT done
        FutureMessage fm1 = new FutureMessage(
            type:FutureMessageType.TEXT,
            message:"hi",
            record:rec1,
            startDate: DateTime.now().minusDays(10),
            isDone:false,
            hasAdjustedDaylightSavings:false,
            whenAdjustDaylightSavings:DateTime.now().minusDays(1),
            daylightSavingsZone:DateTimeZone.forID("America/New_York"))
        fm1.metaClass.refreshTrigger = { -> }
        fm1.save(flush:true, failOnError:true)

        when:
        FutureMessageDaylightSavingsJob job1 = new FutureMessageDaylightSavingsJob()
        job1.execute()

        then:
        FutureMessage.get(fm1.id).hasAdjustedDaylightSavings == true
    }
}
