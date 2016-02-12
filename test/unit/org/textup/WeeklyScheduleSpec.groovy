package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.DateTimeConstants
import org.joda.time.LocalTime
import org.springframework.context.MessageSource
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import org.textup.types.ScheduleStatus
import org.textup.validator.ScheduleChange
import org.textup.validator.LocalInterval

/////////////////////////////////////////////////
// WeeklySchedule assumes all times are in UTC //
/////////////////////////////////////////////////

@Domain([Schedule, WeeklySchedule])
@TestMixin(HibernateTestMixin)
@Unroll
class WeeklyScheduleSpec extends Specification {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		ResultFactory fac = getResultFactory()
		fac.messageSource = [getMessage:{ String code,
			Object[] parameters, Locale locale -> code }] as MessageSource
	}
	private ResultFactory getResultFactory() {
		grailsApplication.mainContext.getBean("resultFactory")
	}

    void "test constraints and deletion"() {
    	given:
    	LocalTime midnight = new LocalTime(0, 0)
    	LocalTime endOfDay = new LocalTime(23, 59)
    	LocalTime t1 = new LocalTime(1, 0)
    	LocalTime t2 = new LocalTime(20, 59)
    	LocalTime t3 = new LocalTime(5, 59)
    	LocalTime t4 = new LocalTime(7, 59)

    	when: "we have an empty schedule"
    	WeeklySchedule s = new WeeklySchedule()
    	s.resultFactory = getResultFactory()

    	then:
    	s.validate() == true

    	when: "update with all valid local intervals for one day"
        LocalInterval lInt1 = new LocalInterval(t1, t2),
            lInt2 = new LocalInterval(midnight, t2)
    	Result res = s.update(monday:[lInt1, lInt2])
    	String mondayString = "0000,2059"

    	then:
    	s.validate() == true
    	res.success == true
    	res.payload.instanceOf(WeeklySchedule)
    	res.payload.monday == mondayString
        res.payload.allAsLocalIntervals.monday.every { it in [lInt1, lInt2] }

    	when: "update all valid for another day, leaving out the first day"
        LocalInterval lInt3 = new LocalInterval(t2, endOfDay),
            lInt4 = new LocalInterval(t3, t4),
            lInt5 = new LocalInterval(t1, t4)
    	res = s.update(tuesday:[lInt3, lInt4, lInt5])

    	then: "both days should be preserved"
    	s.validate() == true
    	res.success == true
    	res.payload.instanceOf(WeeklySchedule)
    	res.payload.monday == mondayString
    	res.payload.tuesday == "0100,0759;2059,2359"
        res.payload.allAsLocalIntervals.monday.every { it in [lInt1, lInt2] }
        res.payload.allAsLocalIntervals.tuesday.every { it in [lInt3, lInt4, lInt5] }

    	when: "update with all valid intervals but some invalid map keys"
	    res = s.update(invalid:[new LocalInterval(t1, t2), new LocalInterval(midnight, t2)])

    	then:
    	s.validate() == true
    	res.success == false
    	res.payload instanceof Throwable

    	when: "update with both the same valid time interval"
	    res = s.update(friday:[new LocalInterval(midnight, t1),
	    	new LocalInterval(t3, t4), new LocalInterval(t3, t4)])

    	then:
    	s.validate() == true
    	res.success == true
    	res.payload.friday == "0000,0100;0559,0759"

    	when: "update with some invalid local intervals"
		res = s.update(tuesday:[new LocalInterval(t2, t1), new LocalInterval(midnight, t2)])

    	then:
    	s.validate() == true
    	res.success == false
    	res.payload instanceof ValidationErrors
    	res.payload.errorCount == 1

    	when: "update string field directly with valid string"
    	String wedString = "0100,0500;0759,2359"
	    s.wednesday = wedString

    	then:
    	s.validate() == true
    	s.wednesday == wedString

    	when: "update string field directly with invalid string"
    	String invalidString = "0100,80 075,10059"
    	s.thursday = invalidString

    	then:
    	s.validate() == false
    	s.errors.errorCount == 1

    	when: "clear field with update method"
    	res = s.update(thursday:[])

    	then:
    	s.validate() == true
    	s.thursday == ""

    	when: "we restore schedule to valid state and then delete it"
    	s.save(flush:true, failOnError:true)
    	int baseline = WeeklySchedule.count()
    	s.delete(flush:true)

    	then:
    	WeeklySchedule.count() == baseline - 1
    }

    void "test updating with interval strings"() {
        given: "a weekly schedule"
        WeeklySchedule s = new WeeklySchedule()
        s.resultFactory = getResultFactory()
        s.save(flush:true, failOnError:true)

        when: "for a certain day, the strings are not in a list"
        Map updateInfo = [monday:"I am not a list"]
        Result res = s.updateWithIntervalStrings(updateInfo)

        then:
        res.success == false
        res.payload instanceof Map
        res.payload.code == "weeklySchedule.strIntsNotList"

        when: "the interval strings are invalidly formatted"
        updateInfo = [monday:["invalid", "0130:0230"]]
        res = s.updateWithIntervalStrings(updateInfo)

        then:
        res.success == false
        res.payload instanceof Map
        res.payload.code == "weeklySchedule.invalidRestTimeFormat"

        when: "we have a map of lists of valid interval strings"
        updateInfo = [monday:["0130:0231", "0230:0330", "0400:0430"]]
        res = s.updateWithIntervalStrings(updateInfo)
        List<LocalInterval> mondayInts = [
            new LocalInterval(new LocalTime(1, 30), new LocalTime(3, 30)),
            new LocalInterval(new LocalTime(4, 0), new LocalTime(4, 30)),
        ]

        then:
        res.success == true
        s.getAllAsLocalIntervals().monday.size() == 2
        s.getAllAsLocalIntervals().monday.every { it in mondayInts }

        when: "we have an interval overlapping at the end"
        updateInfo = [wednesday:["0400:0430", "0330:0430"]]
        res = s.updateWithIntervalStrings(updateInfo)
        List<LocalInterval> wednesdayInts = [
            new LocalInterval(new LocalTime(3, 30), new LocalTime(4, 30))
        ]

        then:
        res.success == true
        s.getAllAsLocalIntervals().wednesday.size() == 1
        s.getAllAsLocalIntervals().wednesday.every { it in wednesdayInts }

        when: "we have an interval overlapping at the end in opposite order"
        updateInfo = [wednesday:["0330:0430", "0400:0430"]]
        res = s.updateWithIntervalStrings(updateInfo)

        then:
        res.success == true
        s.getAllAsLocalIntervals().wednesday.size() == 1
        s.getAllAsLocalIntervals().wednesday.every { it in wednesdayInts }

        when: "we have an interval entirely contained in another"
        updateInfo = [tuesday:["0100:0800", "0330:0430"]]
        res = s.updateWithIntervalStrings(updateInfo)
        List<LocalInterval> tuesdayInts = [
            new LocalInterval(new LocalTime(1, 0), new LocalTime(8, 0))
        ]

        then:
        res.success == true
        s.getAllAsLocalIntervals().tuesday.size() == 1
        s.getAllAsLocalIntervals().tuesday.every { it in tuesdayInts }

        when: "we have an interval entirely contained in another in opposite order"
        updateInfo = [tuesday:["0330:0430", "0100:0800"]]
        res = s.updateWithIntervalStrings(updateInfo)

        then:
        res.success == true
        s.getAllAsLocalIntervals().tuesday.size() == 1
        s.getAllAsLocalIntervals().tuesday.every { it in tuesdayInts }
    }

    void "test updating across different time zones"() {
    	given: "a weekly schedule"
        WeeklySchedule s = new WeeklySchedule()
        s.resultFactory = getResultFactory()
        s.save(flush:true, failOnError:true)
        String tz = "America/New_York"

    	when: "we update from eastern time zone"
    	assert s.updateWithIntervalStrings([monday:["0100:0600", "0530:0730"]], tz).success

    	then: "data is stored in UTC"
    	s.monday == "0600,1230"
    	s.getAllAsLocalIntervals().monday == [new LocalInterval(
    		new LocalTime(6, 0), new LocalTime(12, 30))]

    	and: "we can retrieve in eastern time zone"
    	s.getAllAsLocalIntervals(tz).monday == [new LocalInterval(
    		new LocalTime(1, 0), new LocalTime(7, 30))]

    	when: "we store as UTC, we don't have to specify time zone"
    	assert s.updateWithIntervalStrings([monday:["0100:0600", "0530:0730"]]).success

    	then: "data is stored in UTC"
    	s.monday == "0100,0730"
    	s.getAllAsLocalIntervals().monday == [new LocalInterval(
    		new LocalTime(1, 0), new LocalTime(7, 30))]

    	and: "we can retrieve in eastern time zone"
    	s.getAllAsLocalIntervals(tz).sunday == [new LocalInterval(
    		new LocalTime(20, 0), new LocalTime(23, 59))]
    	s.getAllAsLocalIntervals(tz).monday == [new LocalInterval(
    		new LocalTime(0, 0), new LocalTime(2, 30))]
    }

    void "test updating and asking availability"() {
    	given:
    	LocalTime midnight = new LocalTime(0, 0)
    	LocalTime endOfDay = new LocalTime(23, 59)
    	LocalTime t1 = new LocalTime(1, 0)
    	LocalTime t2 = new LocalTime(20, 59)
    	LocalTime t3 = new LocalTime(5, 59)
    	LocalTime t4 = new LocalTime(7, 59)
    	WeeklySchedule s = new WeeklySchedule()
    	s.resultFactory = getResultFactory()

    	when: "we ask an empty schedule"
    	Result res = s.nextChange()

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "weeklySchedule.nextChangeNotFound"

    	when:
    	res = s.nextAvailable()

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "weeklySchedule.nextChangeNotFound"

    	when:
    	res = s.nextUnavailable()

    	then:
    	res.success == false
    	res.payload instanceof Map
    	res.payload.code == "weeklySchedule.nextChangeNotFound"

    	when: "we add some times"
    	String tomString = getDayOfWeekStringFor(DateTime.now(DateTimeZone.UTC).plusDays(1))
    	s.update((tomString):[new LocalInterval(midnight, t1),
    		new LocalInterval(t3, t4), new LocalInterval(t3, t4),
    		new LocalInterval(t2, endOfDay)])
    	s.save(flush:true, failOnError:true)

    	DateTime availableTime = DateTime.now(DateTimeZone.UTC).plusDays(1).withHourOfDay(6),
    		unavailableTime = DateTime.now(DateTimeZone.UTC).plusDays(1).withHourOfDay(2),
    		tomorrowAvailable = DateTime.now(DateTimeZone.UTC).plusDays(1).withTimeAtStartOfDay(),
    		tomorrowUnavail = tomorrowAvailable.plusHours(1)
    	Result nextChange = s.nextChange(),
    		nextAvail = s.nextAvailable(),
    		nextUnavail = s.nextUnavailable()

    	then:
    	s."$tomString" == "0000,0100;0559,0759;2059,2359"
    	s.isAvailableAt(availableTime) == true
    	s.isAvailableAt(unavailableTime) == false
    	nextChange.payload instanceof ScheduleChange
    	nextChange.payload.type == ScheduleStatus.AVAILABLE
    	nextChange.payload.when == tomorrowAvailable
    	nextAvail.payload instanceof DateTime
    	nextAvail.payload == tomorrowAvailable
    	nextUnavail.payload instanceof DateTime
    	nextUnavail.payload == tomorrowUnavail

    	when: "we test wrapping around times"
    	String todayString = getDayOfWeekStringFor(DateTime.now(DateTimeZone.UTC)),
    		followingString = getDayOfWeekStringFor(DateTime.now(DateTimeZone.UTC).plusDays(2))
    	s.update((todayString):[], (tomString):[new LocalInterval(t2, endOfDay)],
    		(followingString):[new LocalInterval(midnight, t1)])
    	s.save(flush:true, failOnError:true)

    	nextChange = s.nextChange()
		nextAvail = s.nextAvailable()
		nextUnavail = s.nextUnavailable()
		DateTime nextAvailTime = DateTime.now(DateTimeZone.UTC).plusDays(1)
				.withHourOfDay(20).withMinuteOfHour(59)
				.withSecondOfMinute(0).withMillisOfSecond(0),
			nextUnavailTime = DateTime.now(DateTimeZone.UTC).plusDays(2)
				.withHourOfDay(1).withMinuteOfHour(00)
				.withSecondOfMinute(0).withMillisOfSecond(0)

    	then:
    	s."$todayString" == ""
    	s."$tomString" == "2059,2359"
    	s."$followingString" == "0000,0100"
    	nextChange.payload instanceof ScheduleChange
    	nextChange.payload.type == ScheduleStatus.AVAILABLE
    	nextChange.payload.when == nextAvailTime
    	nextAvail.payload instanceof DateTime
    	nextAvail.payload == nextAvailTime
    	nextUnavail.payload instanceof DateTime
    	nextUnavail.payload == nextUnavailTime

    }
    private String getDayOfWeekStringFor(DateTime dt) {
    	switch(dt.dayOfWeek) {
            case DateTimeConstants.SUNDAY: return "sunday"
            case DateTimeConstants.MONDAY: return "monday"
            case DateTimeConstants.TUESDAY: return "tuesday"
            case DateTimeConstants.WEDNESDAY: return "wednesday"
            case DateTimeConstants.THURSDAY: return "thursday"
            case DateTimeConstants.FRIDAY: return "friday"
            case DateTimeConstants.SATURDAY: return "saturday"
        }
    }
}
