package org.textup.validator

import org.joda.time.LocalTime
import spock.lang.Specification
import grails.test.mixin.support.GrailsUnitTestMixin

@TestMixin(GrailsUnitTestMixin)
class LocalIntervalSpec extends Specification {

    void "test constraints"() {
    	when: "we have an interval missing fields"
    	LocalInterval l = new LocalInterval()

    	then:
    	l.validate() == false
    	l.errors.errorCount == 2

    	when: "we have a valid interval"
    	LocalTime start = LocalTime.now(), end = start.plusMinutes(1)
    	l = new LocalInterval(start:start, end:end)

    	then:
    	l.validate() == true

    	when: "we have a zero length interval"
    	LocalTime t = LocalTime.now()
    	l = new LocalInterval(start:t, end:t)

    	then: "valid"
    	l.validate() == true

    	when: "we have a valid interval starting at midnight"
    	LocalTime midnight = new LocalTime(0, 0), afterMidnight = midnight.plusMinutes(10)
    	l = new LocalInterval(start:midnight, end:afterMidnight)

    	then:
    	l.validate() == true

    	when: "we have an interval at the end of the day"
    	LocalTime endOfDay = new LocalTime(23, 59), beforeEnd = endOfDay.minusMinutes(10)
    	l = new LocalInterval(start:beforeEnd, end:endOfDay)

    	then:
    	l.validate() == true

    	when:"we have an interval that spans multiple days"
    	l = new LocalInterval(start:end, end:start)

    	then: "invalid, local intervals must be within the same day"
    	l.validate() == false
    	l.errors.errorCount == 1
    }

    void "test sorting, overlap and abuts"() {
    	given:
    	LocalTime midnight = new LocalTime(0, 0)
    	LocalTime endOfDay = new LocalTime(23, 59)
    	LocalTime time1 = new LocalTime(1, 0)
    	LocalTime time2 = new LocalTime(20, 59)

    	LocalInterval l1 = new LocalInterval(start:midnight, end:time2),
    		l2 = new LocalInterval(start:midnight, end:endOfDay),
    		l3 = new LocalInterval(start:time1, end:time2),
    		l4 = new LocalInterval(start:time1, end:endOfDay),
    		l5 = new LocalInterval(start:time2, end:endOfDay)
    	assert [l1, l2, l3, l4, l5].every { it.validate() }

    	expect:
    	//sort based on first start time, then end time
    	[l3, l2, l1, l5, l4].sort() == [l1, l2, l3, l4, l5]

    	l1.abuts(l5) && l5.abuts(l1)
    	l3.abuts(l5) && l5.abuts(l3)
    	!l3.abuts(l4) && !l4.abuts(l3) //these two overlap, not abut
    	l3.overlaps(l4) && l4.overlaps(l3)
    	[l1, l3, l4, l5].every { l2.overlaps(it) && !l2.abuts(it) }
    }
}
