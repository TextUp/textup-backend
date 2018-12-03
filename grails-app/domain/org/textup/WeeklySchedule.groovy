package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.Interval
import org.joda.time.LocalTime
import org.restapidoc.annotation.*
import org.springframework.validation.Errors
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.LocalInterval
import org.textup.validator.ScheduleChange

// [NOTE] All times are stored in UTC!

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="Schedule", description="Schedule that repeats weekly.")
class WeeklySchedule extends Schedule {

    @RestApiObjectField(
        description    = "Available times on Sunday. Strings must be in format \
            'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String sunday = ""
    @RestApiObjectField(
        description    = "Available times on Monday. Strings must be in format \
            'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String monday = ""
    @RestApiObjectField(
        description    = "Available times on Tuesday. Strings must be in format \
            'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String tuesday = ""
    @RestApiObjectField(
        description    = "Available times on Wednesday. Strings must be in format \
            'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String wednesday = ""
    @RestApiObjectField(
        description    = "Available times on Thursday. Strings must be in format \
            'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String thursday = ""
    @RestApiObjectField(
        description    = "Available times on Friday. Strings must be in format \
            'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String friday = ""
    @RestApiObjectField(
        description    = "Available times on Saturday. Strings must be in format \
            'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String saturday = ""

    protected String _rangeDelimiter = ";"
    protected String _timeDelimiter = ","
    protected String _restDelimiter = ":"
    protected String _timeFormat = "HHmm"

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName= "nextAvailable",
            description = "Date and time of the next time the schedule will be \
                available. Only appears if the schedule is not empty.",
            allowedType =  "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "nextUnavailable",
            description = "Date and time of the next time the schedule will be \
                unavailable. Only appears if the schedule is not empty.",
            allowedType =  "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "isAvailableNow",
            description    = "If the schedule shows that it is available right now.",
            allowedType    = "Boolean",
            useForCreation = false),
    ])
    static constraints = {
        sunday validator:{ String val, WeeklySchedule obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        monday validator:{ String val, WeeklySchedule obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        tuesday validator:{ String val, WeeklySchedule obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        wednesday validator:{ String val, WeeklySchedule obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        thursday validator:{ String val, WeeklySchedule obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        friday validator:{ String val, WeeklySchedule obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        saturday validator:{ String val, WeeklySchedule obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
    }

    // Accessible methods
    // ------------------

    @Override
    boolean isAvailableAt(DateTime dt) {
        if (dt) {
            List<Interval> intervals = rehydrateAsIntervals(dt)
            intervals.any { Interval interval -> interval.contains(dt) }
        }
        else { false }
    }
    @Override
    Result<ScheduleChange> nextChange(String timezone=null) {
        DateTime now = DateTime.now(DateTimeZone.UTC)
        nextChangeForDateTime(now, now, timezone)
    }
    @Override
    Result<DateTime> nextAvailable(String timezone=null) {
        Result<ScheduleChange> res = nextChangeForType(ScheduleStatus.AVAILABLE, timezone)
        if (res.success) {
            IOCUtils.resultFactory.success(res.payload.when)
        }
        else { IOCUtils.resultFactory.failWithResultsAndStatus([res], res.status) }
    }
    @Override
    Result<DateTime> nextUnavailable(String timezone=null) {
        Result<ScheduleChange> res = nextChangeForType(ScheduleStatus.UNAVAILABLE, timezone)
        if (res.success) {
            IOCUtils.resultFactory.success(res.payload.when)
        }
        else { IOCUtils.resultFactory.failWithResultsAndStatus([res], res.status) }
    }
    @Override
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    Result<Schedule> update(Map<String,List<LocalInterval>> params) {
        try {
            Errors errors = null
            for (i in params?.values()?.flatten()) {
                LocalInterval li = i as LocalInterval
                if (!li.validate()) { errors = li.errors; break; }
            }
            if (errors) {
                IOCUtils.resultFactory.failWithValidationErrors(errors)
            }
            else {
                params.each { String key, List<LocalInterval> intervals ->
                    this."$key" = intervals.isEmpty() ? "" :
                        dehydrateLocalIntervals(cleanLocalIntervals(intervals))
                }
                IOCUtils.resultFactory.success(this)
            }
        }
        catch (Throwable e) {
            log.debug("WeeklySchedule.update: ${e.message}")
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }
    Result<Schedule> updateWithIntervalStrings(Map<String,List<String>> params, String timezone="UTC") {
        DateTimeZone zone = DateTimeUtils.getZoneFromId(timezone)
        List<String> daysOfWeek = Constants.DAYS_OF_WEEK
        int numDaysPerWeek = daysOfWeek.size()
        //parse interval strings into a list of UTC intervals where sunday corresponds to
        //today, monday corresponds to tomorrow and so forth
        List<Interval> utcIntervals = []
        for(int addDays = 0; addDays < numDaysPerWeek; addDays++) {
            String dayOfWeek = daysOfWeek[addDays]
            def intStrings = params[dayOfWeek]
            if (intStrings) {
                if (intStrings instanceof List) {
                    Result res = parseIntervalStringsToUTCIntervals(intStrings as List,
                        addDays, zone)
                    if (res.success) {
                        utcIntervals += res.payload
                    }
                    else { return res }
                }
                else {
                    return IOCUtils.resultFactory.failWithCodeAndStatus("weeklySchedule.strIntsNotList",
                        ResultStatus.UNPROCESSABLE_ENTITY, [intStrings])
                }
            }
        }
        //call update after converting interval strings to local intervals
        update(fromIntervalsToLocalIntervalsMap(utcIntervals))
    }

    // Property Access
    // ---------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    Map<String,List<LocalInterval>> getAllAsLocalIntervals(String timezone="UTC") {
        DateTimeZone zone = DateTimeUtils.getZoneFromId(timezone)
        DateTimeFormatter dtf = DateTimeFormat.forPattern(_timeFormat).withZoneUTC()
        List<String> daysOfWeek = Constants.DAYS_OF_WEEK
        //rehydrate the strings as INTERVALS where sunday corresponds to TODAY, monday corresponds
        //to tomorrow, and tuesday corresponds to the day after tomorrow, etc.
        List<Interval> intervals = []
        //Handle edge case where Sunday's range is actually
        //a wraparound of a range on Saturday!
        boolean hasWraparound
        String firstDayWrappedEnd
        (hasWraparound, firstDayWrappedEnd) = checkWraparoundHelper(daysOfWeek)
        //iterate over each day, building intervals as appropriate
        daysOfWeek.eachWithIndex { String dayOfWeek, int addDays ->
            List<Interval> intervalsForDay = []
            this."$dayOfWeek".tokenize(_rangeDelimiter).each { String rangeString ->
                List<String> times = rangeString.tokenize(_timeDelimiter)
                if (times.size() == 2) {
                    //if is Sunday
                    if (dayOfWeek == daysOfWeek[0]) {
                        if (!(hasWraparound && times[0] == "0000")) {
                            addToIntervalsHelper(dtf, zone, intervalsForDay, times, addDays)
                        }
                    }
                    // should add wraparound on Saturday
                    else if (dayOfWeek == daysOfWeek.last()) {
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
                            addToIntervalsHelper(dtf, zone, intervalsForDay, times, addDays)
                        }
                    }
                    //handle all other days
                    else { addToIntervalsHelper(dtf, zone, intervalsForDay, times, addDays) }
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
        fromIntervalsToLocalIntervalsMap(intervals)
            .each { String dayOfWeek, List<LocalInterval> localInts ->
                //1 minute merge threshold
                mergedLocalIntMap[dayOfWeek] = cleanLocalIntervals(localInts.sort(), 1)
            }
        mergedLocalIntMap
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected List checkWraparoundHelper(List<String> daysOfWeek) {
        boolean lastDayAtEnd = false,
            firstDayAtBeginning = false
        String firstDayWrappedEnd
        for (wrapRange in this."${daysOfWeek[0]}".tokenize(_rangeDelimiter)) {
            if (wrapRange.tokenize(_timeDelimiter)[0] == "0000") {
                firstDayWrappedEnd = wrapRange.tokenize(_timeDelimiter)[1]
                firstDayAtBeginning = true
                break
            }
        }
        for (wrapRange in this."${daysOfWeek.last()}".tokenize(_rangeDelimiter)) {
            if (wrapRange.tokenize(_timeDelimiter)[1] == "2359") {
                lastDayAtEnd = true
                break
            }
        }
        boolean hasWraparound = lastDayAtEnd && firstDayAtBeginning
        [hasWraparound, firstDayWrappedEnd]
    }
    protected addToIntervalsHelper(DateTimeFormatter dtf, DateTimeZone zone,
        List<Interval> intervalsForDay, List<String> times, int addDays) {

        DateTime start = DateTimeUtils.toUTCDateTimeTodayThenZone(dtf.parseLocalTime(times[0]), zone)
            .plusDays(addDays)
        DateTime end = DateTimeUtils.toUTCDateTimeTodayThenZone(dtf.parseLocalTime(times[1]), zone)
            .plusDays(addDays)
        intervalsForDay << new Interval(start, end)
    }

    // Next change
    // -----------

    protected Result<ScheduleChange> nextChangeForType(ScheduleStatus type, String timezone) {
        DateTime now = DateTime.now()
        Result<ScheduleChange> res = nextChangeForDateTime(now, now, timezone)
        if (res.success) {
            ScheduleChange sChange = res.payload
            if (sChange.type != type) {
                res = nextChangeForDateTime(sChange.when, sChange.when, timezone)
            }
        }
        res
    }
    protected Result<ScheduleChange> nextChangeForDateTime(DateTime dt,
        DateTime initialDt, String timezone) {
        List<Interval> intervals = rehydrateAsIntervals(dt)
        ScheduleChange sChange = null
        //set the closest upcoming to be impossibly far into the future as an initial value
        DateTime closestUpcoming = dt.plusWeeks(1)
        for (interval in intervals) {
            if (interval.contains(initialDt)) {
                sChange = new ScheduleChange(type:ScheduleStatus.UNAVAILABLE,
                    when:interval.end, timezone:timezone)
                break
            }
            else if (interval.isBefore(closestUpcoming) && interval.isAfter(initialDt)) {
                closestUpcoming = interval.start
            }
        }
        //If sChange is not null, then we found an interval that contains initialDt
        if (sChange) { IOCUtils.resultFactory.success(sChange) }
        //Otherwise, we need to find the nearest upcoming interval
        else if (closestUpcoming && !intervals.isEmpty()) {
            IOCUtils.resultFactory.success(new ScheduleChange(type:ScheduleStatus.AVAILABLE,
                when:closestUpcoming, timezone:timezone))
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

    // Handle non-UTC timezones
    // ------------------------

    // iterate each interval, and bin them into the appropriate day of the week,
    // assuming that sunday corresponds to day, monday to tomorrow and so forth.
    // For wraparound purposes, yesterday corresponds to saturday
    protected Map<String,List<LocalInterval>> fromIntervalsToLocalIntervalsMap(List<Interval> intervals) {
        List<String> daysOfWeek = Constants.DAYS_OF_WEEK
        Map<String,List<LocalInterval>> localIntervals = daysOfWeek.collectEntries { [(it):[]] }
        DateTime today = DateTime.now(DateTimeZone.UTC)
        intervals.each { Interval interval ->
            int startDayOfWeek = DateTimeUtils
                    .getDayOfWeekIndex(DateTimeUtils.getDaysBetween(today, interval.start)),
                endDayOfWeek = DateTimeUtils
                    .getDayOfWeekIndex(DateTimeUtils.getDaysBetween(today, interval.end))
            String startDay = daysOfWeek[startDayOfWeek],
                    endDay = daysOfWeek[endDayOfWeek]
            //if interval does not span two days
            if (startDayOfWeek == endDayOfWeek) {
                localIntervals[startDay] << new LocalInterval(interval)
            }
            else { //interval spans two days, break interval into two local intervals
                localIntervals[startDay] << new LocalInterval(interval.start.toLocalTime(),
                    new LocalTime(23, 59))
                localIntervals[endDay] << new LocalInterval(new LocalTime(00, 00),
                    interval.end.toLocalTime())
            }
        }
        localIntervals
    }

    protected Result<List<Interval>> parseIntervalStringsToUTCIntervals(List<String> intStrings,
        int addDays, DateTimeZone zone=null) {
        List<Interval> result = []
        DateTimeFormatter dtf = DateTimeFormat.forPattern(_timeFormat).withZoneUTC()
        DateTime today = DateTime.now(DateTimeZone.UTC),
            zoneToday = DateTime.now(zone)
        // order of the arguments in this call is very important
        // we put the zone first so that the result number is the modifier that we
        // can use to scale the addDays amount by.
        int daysBetween = DateTimeUtils.getDaysBetween(zoneToday, today)

        for (str in intStrings) {
            try {
                List<String> times = str.tokenize(_restDelimiter)
                if (times.size() == 2) {
                    DateTime start = DateTimeUtils
                            .toZoneDateTimeTodayThenUTC(dtf.parseLocalTime(times[0]), zone),
                        end = DateTimeUtils
                            .toZoneDateTimeTodayThenUTC(dtf.parseLocalTime(times[1]), zone)
                    //add days until we've reached the desired offset from today
                    //we use the start date as reference and increment the end date
                    //accordingly to preserve the range
                    //NO NEED to take into account the # days between start/end date and today here
                    //because if the start/end dates are actually a day ahead or behind because
                    //of timezone differences, the helper to parse the strings into DateTime objects
                    //already take that into account. All we need to do is boost by the correct
                    //number of days here scaled by the number of days between UTC today and timezone today
                    (addDays + daysBetween).times {
                        start = start.plusDays(1)
                        end = end.plusDays(1)
                    }
                    result << new Interval(start, end)
                }
                else {
                    return IOCUtils.resultFactory.failWithCodeAndStatus("weeklySchedule.invalidRestTimeFormat",
                        ResultStatus.UNPROCESSABLE_ENTITY, [str])
                }
            }
            catch (e) {
                log.debug("WeeklyScheduleSpec.parseIntervalStrings: ${e.message}")
                return IOCUtils.resultFactory.failWithCodeAndStatus("weeklySchedule.invalidRestTimeFormat",
                    ResultStatus.UNPROCESSABLE_ENTITY, [str])
            }
        }
        IOCUtils.resultFactory.success(result)
    }

    // Dehydrate, rehydrate and validate intervals
    // -------------------------------------------

    protected List<Interval> rehydrateAsIntervals(DateTime dt, boolean stitchEndOfDay=true) {
        String intervalsString = getDayOfWeek(dt)
        DateTimeFormatter dtf = DateTimeFormat.forPattern(_timeFormat).withZoneUTC()
        Interval endOfDayInterval = null //stitch intervals that cross between days
        List<Interval> intervals = []
        intervalsString.tokenize(_rangeDelimiter).each { String rangeString ->
            List<String> times = rangeString.tokenize(_timeDelimiter)
            if (times.size() == 2) {
                DateTime start = dtf.parseLocalTime(times[0])
                    .toDateTime(dt).withZoneRetainFields(DateTimeZone.UTC)
                DateTime end = dtf.parseLocalTime(times[1])
                    .toDateTime(dt).withZoneRetainFields(DateTimeZone.UTC)
                Interval interval = new Interval(start, end)
                //if end of day interval, don't add it to intervals list yet
                if (isEndOfDay(end)) { endOfDayInterval = interval }
                else { intervals << interval }
            }
            else {
                log.error("WeeklySchedule.rehydrateAsIntervals: \
                    for intervals $intervalsString, invalid range: $rangeString")
            }
        }
        if (stitchEndOfDay && endOfDayInterval) {
            List<Interval> tomorrowIntervals = rehydrateAsIntervals(dt.plusDays(1), false)
            Interval startOfDayInterval = tomorrowIntervals.find { isStartOfDay(it.start) }
            if (startOfDayInterval) {
                endOfDayInterval = new Interval(endOfDayInterval.start, startOfDayInterval.end)
            }
        }
        if (endOfDayInterval) {
            intervals << endOfDayInterval
        }
        intervals
    }
    protected boolean isEndOfDay(DateTime dt) { dt.plusMinutes(2).dayOfWeek != dt.dayOfWeek }
    protected boolean isStartOfDay(DateTime dt) { dt.minusMinutes(2).dayOfWeek != dt.dayOfWeek }
    protected String getDayOfWeek(DateTime dt) {
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

    protected List<LocalInterval> cleanLocalIntervals(List<LocalInterval> intervals,
        Integer minutesThreshold=null) {
        List<LocalInterval> sorted = intervals.sort(),
            cleaned = sorted.isEmpty() ? new ArrayList<LocalInterval>() : [sorted[0]]
        int sortedLen = sorted.size()
        boolean mergedPrevious = true
        for(int i = 1; i < sortedLen; i++) {
            LocalInterval int1 = sorted[i - 1], int2 = sorted[i]
            if (int1 != int2) {
                if (int1.abuts(int2) || int1.overlaps(int2) ||
                    (minutesThreshold && int1.withinMinutesOf(int2, minutesThreshold))) {

                    LocalInterval startingInt = mergedPrevious ? cleaned.last() : int1
                    cleaned = (cleaned.size() == 1) ?
                        new ArrayList<LocalInterval>() :
                        cleaned[0..-2] //pop last
                    if (startingInt.end > int2.end) {
                        cleaned << new LocalInterval(start:startingInt.start, end:startingInt.end)
                    }
                    else {
                        cleaned << new LocalInterval(start:startingInt.start, end:int2.end)
                    }
                    mergedPrevious = true
                }
                else {
                    cleaned << int2
                    mergedPrevious = false
                }
            }
        }
        cleaned
    }
    protected String dehydrateLocalIntervals(List<LocalInterval> intervals) {
        List<String> intStrings = []
        DateTimeFormatter dtf = DateTimeFormat.forPattern(_timeFormat).withZoneUTC()
        intervals.each { LocalInterval i ->
            intStrings << "${dtf.print(i.start)}${_timeDelimiter}${dtf.print(i.end)}".toString()
        }
        intStrings.join(_rangeDelimiter)
    }
    protected boolean validateIntervalsString(String str) {
        List<String> strInts = str.tokenize(_rangeDelimiter)
        DateTimeFormatter dtf = DateTimeFormat.forPattern(_timeFormat).withZoneUTC()
        List<LocalTime> times = []
        try {
            for (rangeString in strInts) {
                List<String> rTimes = rangeString.tokenize(_timeDelimiter)
                if (rTimes.size() != 2) { return false }
                LocalTime start = dtf.parseLocalTime(rTimes[0]),
                    end = dtf.parseLocalTime(rTimes[1])
                if (start.isAfter(end) ||
                    !times.isEmpty() && times.last().isAfter(start)) { return false }
                (times << start) << end
            }
            return true
        }
        catch (e) {
            return false
        }
    }
}
