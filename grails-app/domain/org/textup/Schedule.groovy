package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.DateTimeConstants
import org.joda.time.format.DateTimeFormatter
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.joda.time.Interval

@GrailsTypeChecked
@EqualsAndHashCode
class Schedule implements WithId, CanSave<Schedule> {

    boolean manual = true
    boolean manualIsAvailable = true

    String sunday = ""
    String monday = ""
    String tuesday = ""
    String wednesday = ""
    String thursday = ""
    String friday = ""
    String saturday = ""

    static constraints = {
        sunday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        monday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        tuesday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        wednesday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        thursday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        friday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
        saturday validator:{ String val ->
            if (!ScheduleUtils.validateIntervalsString(val)) { ["invalid"] }
        }
    }

    static Result<Schedule> tryCreate() {
        DomainUtils.trySave(new Schedule(), ResultStatus.CREATED)
    }

    // Methods
    // -------

    boolean isAvailableNow() {
        isAvailableAt(JodaUtils.now())
    }

    boolean isAvailableAt(DateTime dt) {
        if (manual) {
            manualIsAvailable
        }
        else {
            if (dt) {
                LocalIntervalUtils
                    .rehydrateAsIntervals(dt) { DateTime dt1 ->
                        getValueForDayOfWeek(dt1)
                    }
                    .any { Interval interval -> interval.contains(dt) }
            }
            else { false }
        }
    }

    DateTime nextAvailable(String timezone = null) {
        if (manual) {
            null
        }
        else { nextChangeForType(ScheduleStatus.AVAILABLE, timezone)?.payload?.when }
    }

    DateTime nextUnavailable(String timezone = null) {
        if (manual) {
            null
        }
        else { nextChangeForType(ScheduleStatus.UNAVAILABLE, timezone)?.payload?.when }
    }

    // parse interval strings into a list of UTC intervals where sunday corresponds to today, etc
    Result<Schedule> updateWithIntervalStrings(TypeMap body,
        String timezone = ScheduleUtils.DEFAULT_TIMEZONE) {

        DateTimeZone zone = JodaUtils.getZoneFromId(timezone)
        int numDaysPerWeek = ScheduleUtils.DAYS_OF_WEEK.size()
        ResultGroup<List<Interval>> resGroup = new ResultGroup<>()
        for(int addDays = 0; addDays < numDaysPerWeek; ++addDays) {
            String dayOfWeek = ScheduleUtils.DAYS_OF_WEEK[addDays]
            List<String> intStrings = body.typedList(String, dayOfWeek)
            resGroup << ScheduleUtils.parseIntervalStringsToUTCIntervals(intStrings, addDays, zone)
        }
        resGroup.toResult(false).then { List<List<Interval>> intervals ->
            Map<String, List<LocalInterval>> dayToIntervals = LocalIntervalUtils
                .fromIntervalsToLocalIntervalsMap(CollectionUtils.mergeUnique(intervals))
            updateLocalIntervals(dayToIntervals)
        }
    }

    // Properties
    // ----------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    Map<String,List<LocalInterval>> getAllAsLocalIntervals(String timezone = ScheduleUtils.DEFAULT_TIMEZONE) {
        DateTimeZone zone = JodaUtils.getZoneFromId(timezone)
        DateTimeFormatter dtf = DateTimeFormat.forPattern(ScheduleUtils.TIME_FORMAT).withZoneUTC()
        //rehydrate the strings as INTERVALS where sunday corresponds to TODAY, monday corresponds
        //to tomorrow, and tuesday corresponds to the day after tomorrow, etc.
        List<Interval> intervals = []
        //Handle edge case where Sunday's range is actually
        //a wraparound of a range on Saturday!
        boolean hasWraparound
        String firstDayWrappedEnd
        (hasWraparound, firstDayWrappedEnd) = checkWraparoundHelper()
        //iterate over each day, building intervals as appropriate
        ScheduleUtils.DAYS_OF_WEEK.eachWithIndex { String dayOfWeek, int addDays ->
            List<Interval> intervalsForDay = []
            this."$dayOfWeek".tokenize(ScheduleUtils.RANGE_DELIMITER).each { String rangeString ->
                List<String> times = rangeString.tokenize(ScheduleUtils.TIME_DELIMITER)
                if (times.size() == 2) {
                    //if is Sunday
                    if (dayOfWeek == ScheduleUtils.DAYS_OF_WEEK[0]) {
                        if (!(hasWraparound && times[0] == "0000")) {
                            intervalsForDay <<
                                ScheduleUtils.buildIntervalFromStrings(dtf, zone, times, addDays)
                        }
                    }
                    // should add wraparound on Saturday
                    else if (dayOfWeek == ScheduleUtils.DAYS_OF_WEEK.last()) {
                        if (hasWraparound && times[1] == "2359") {
                            DateTime start = DateTimeUtils
                                .toUTCDateTimeTodayThenZone(dtf.parseLocalTime(times[0]), zone)
                                .plusDays(addDays)
                            DateTime end = DateTimeUtils
                                .toUTCDateTimeTodayThenZone(dtf.parseLocalTime(firstDayWrappedEnd), zone)
                                .plusDays(addDays + 1)
                            intervalsForDay << new Interval(start, end)
                        }
                        else {
                            intervalsForDay <<
                                ScheduleUtils.buildIntervalFromStrings(dtf, zone, times, addDays)
                        }
                    }
                    //handle all other days
                    else {
                        intervalsForDay <<
                            ScheduleUtils.buildIntervalFromStrings(dtf, zone, times, addDays)
                    }
                }
                else {
                    log.error("WeeklySchedule.getAsLocalIntervals: \
                        for $dayOfWeek, invalid range: $rangeString")
                }
            }
            intervals += intervalsForDay
        }
        //finally clean to merge abutting local intervals on the same day
        Map<String,List<LocalInterval>> mergedLocalIntMap = [:]
        LocalIntervalUtils.fromIntervalsToLocalIntervalsMap(intervals)
            .each { String dayOfWeek, List<LocalInterval> localInts ->
                //1 minute merge threshold
                mergedLocalIntMap[dayOfWeek] = LocalIntervalUtils.cleanLocalIntervals(localInts.sort(), 1)
            }
        mergedLocalIntMap
    }

    // Helpers
    // -------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected Result<Schedule> updateLocalIntervals(Map<String, List<LocalInterval>> dayToIntervals) {
        try {
            DomainUtils.tryValidateAll(CollectionUtils.mergeUnique(dayToIntervals?.values()))
                .then {
                    dayToIntervals.each { String key, List<LocalInterval> intervals ->
                        if (intervals.isEmpty()) {
                            this."$key" = ""
                        }
                        else {
                            this."$key" =
                                LocalIntervalUtils.dehydrateLocalIntervals(LocalIntervalUtils.cleanLocalIntervals(intervals))
                        }
                    }
                    DomainUtils.trySave(this)
                }
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "updateLocalIntervals")
        }
    }

    protected String getValueForDayOfWeek(DateTime dt) {
        switch(dt.dayOfWeek) {
            case DateTimeConstants.SUNDAY: return sunday
            case DateTimeConstants.MONDAY: return monday
            case DateTimeConstants.TUESDAY: return tuesday
            case DateTimeConstants.WEDNESDAY: return wednesday
            case DateTimeConstants.THURSDAY: return thursday
            case DateTimeConstants.FRIDAY: return friday
            case DateTimeConstants.SATURDAY: return saturday
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected List checkWraparoundHelper() {
        boolean lastDayAtEnd = false,
            firstDayAtBeginning = false
        String firstDayWrappedEnd
        for (wrapRange in this."${ScheduleUtils.DAYS_OF_WEEK[0]}".tokenize(RANGE_DELIMITER)) {
            if (wrapRange.tokenize(TIME_DELIMITER)[0] == "0000") {
                firstDayWrappedEnd = wrapRange.tokenize(TIME_DELIMITER)[1]
                firstDayAtBeginning = true
                break
            }
        }
        for (wrapRange in this."${ScheduleUtils.DAYS_OF_WEEK.last()}".tokenize(RANGE_DELIMITER)) {
            if (wrapRange.tokenize(TIME_DELIMITER)[1] == "2359") {
                lastDayAtEnd = true
                break
            }
        }
        boolean hasWraparound = lastDayAtEnd && firstDayAtBeginning
        [hasWraparound, firstDayWrappedEnd]
    }

    protected Result<ScheduleChange> nextChangeForType(ScheduleStatus type, String timezone) {
        DateTime now = DateTime.now()

        nextChangeForDateTime(now, now, timezone)
            .then { ScheduleChange sChange ->
                if (sChange.type != type) {
                    nextChangeForDateTime(sChange.when, sChange.when, timezone)
                }
                else { IOCUtils.resultFactory.success(sChange) }
            }
    }

    protected Result<ScheduleChange> nextChangeForDateTime(DateTime dt,
        DateTime initialDt, String timezone) {

        List<Interval> intervals = LocalIntervalUtils
            .rehydrateAsIntervals(dt) { DateTime dt1 -> getValueForDayOfWeek(dt1) }
        ScheduleChange sChange1
        //set the closest upcoming to be impossibly far into the future as an initial value
        DateTime closestUpcoming = dt.plusWeeks(1)
        for (interval in intervals) {
            if (interval.contains(initialDt)) {
                sChange1 = new ScheduleChange(type: ScheduleStatus.UNAVAILABLE,
                    when: interval.end, timezone: timezone)
                break
            }
            else if (interval.isBefore(closestUpcoming) && interval.isAfter(initialDt)) {
                closestUpcoming = interval.start
            }
        }
        //If sChange is not null, then we found an interval that contains initialDt
        if (sChange1) {
            IOCUtils.resultFactory.success(sChange1)
        }
        //Otherwise, we need to find the nearest upcoming interval
        else if (closestUpcoming && !intervals.isEmpty()) {
            ScheduleChange sChange2 = new ScheduleChange(type: ScheduleStatus.AVAILABLE,
                when: closestUpcoming, timezone: timezone)
            IOCUtils.resultFactory.success(sChange2)
        }
        //If no upcoming intervals on this day, check the next day
        else {
            //continue searching if we have not yet looked one week into the future
            if (initialDt.plusWeeks(1) != dt) {
                nextChangeForDateTime(dt.plusDays(1), initialDt, timezone)
            }
            else {
                IOCUtils.resultFactory.failWithCodeAndStatus("weeklySchedule.nextChangeNotFound",
                    ResultStatus.NOT_FOUND)
            }
        }
    }
}
