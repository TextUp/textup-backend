package org.textup

import grails.validation.ValidationErrors
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.Interval
import org.joda.time.LocalTime
import org.restapidoc.annotation.*

@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="Schedule", description="Schedule that repeats weekly.")
class WeeklySchedule extends Schedule {

    def resultFactory

    /////////////////////////////////
    // All times are stored in UTC //
    /////////////////////////////////

    @RestApiObjectField(
        description    = "Available times on Sunday. Strings must be in format 'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String sunday = ""
    @RestApiObjectField(
        description    = "Available times on Monday. Strings must be in format 'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String monday = ""
    @RestApiObjectField(
        description    = "Available times on Tuesday. Strings must be in format 'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String tuesday = ""
    @RestApiObjectField(
        description    = "Available times on Wednesday. Strings must be in format 'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String wednesday = ""
    @RestApiObjectField(
        description    = "Available times on Thursday. Strings must be in format 'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String thursday = ""
    @RestApiObjectField(
        description    = "Available times on Friday. Strings must be in format 'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String friday = ""
    @RestApiObjectField(
        description    = "Available times on Saturday. Strings must be in format 'HHmm:HHmm'. We expect all times passed into already be converted into UTC.",
        allowedType    = "List<String>",
        defaultValue   = "",
        mandatory      = false,
        useForCreation = true)
	String saturday = ""

    private String _rangeDelimiter = ";"
    private String _timeDelimiter = ","
    private String _restDelimiter = ":"
    private String _timeFormat = "HHmm"

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName= "nextAvailable",
            description = "Date and time of the next time the schedule will be available. Only appears if the schedule is not empty.",
            allowedType =  "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "nextUnavailable",
            description = "Date and time of the next time the schedule will be unavailable. Only appears if the schedule is not empty.",
            allowedType =  "DateTime",
            useForCreation = false),
    ])
    static transients=[]
    static constraints = {
        sunday validator:{ val, obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        monday validator:{ val, obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        tuesday validator:{ val, obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        wednesday validator:{ val, obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        thursday validator:{ val, obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        friday validator:{ val, obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
        saturday validator:{ val, obj ->
            if (!obj.validateIntervalsString(val)) { ["invalid"] }
        }
    }

    /*
	Has many:
	*/

    ////////////////////////
    // Accessible methods //
    ////////////////////////

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
        nextChangeForDateTime(now, now)
    }
    @Override
    Result<DateTime> nextAvailable(String timezone=null) {
        Result res = nextChangeForType(Constants.SCHEDULE_AVAILABLE)
        if (res.success) { resultFactory.success(res.payload.when) }
        else { res }
    }
    @Override
    Result<DateTime> nextUnavailable(String timezone=null) {
        Result res = nextChangeForType(Constants.SCHEDULE_UNAVAILABLE)
        if (res.success) { resultFactory.success(res.payload.when) }
        else { res }
    }
    @Override
    Result<Schedule> update(Map<String,List<LocalInterval>> params, String timezone=null) {
        try {
            ValidationErrors errors = null
            for (i in params?.values()?.flatten()) {
                if (!i.validate()) { errors = i.errors; break; }
            }
            if (errors) { resultFactory.failWithValidationErrors(errors) }
            else {
                params.each { String key, List<LocalInterval> intervals ->
                    this."$key" = intervals.isEmpty() ? "" : dehydrateLocalIntervals(cleanLocalIntervals(intervals))
                }
                resultFactory.success(this)
            }
        }
        catch (Throwable e) {
            log.debug("WeeklySchedule.update: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
    Result<Schedule> updateWithIntervalStrings(Map<String,List<String>> params, String timezone=null) {
        Map<String, List<LocalInterval>> localIntervalParams = [:]
        for (dayEntry in params) {
            if (dayEntry.value instanceof List) {
                Result res = parseIntervalStrings(dayEntry.value)
                if (res.success) {
                    localIntervalParams."${dayEntry.key}" = res.payload
                }
                else { return res }
            }
            else {
                return resultFactory.failWithMessage("weeklySchedule.error.strIntsNotList",
                    [dayEntry.key])
            }
        }
        update(localIntervalParams)
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    protected Result<List<LocalInterval>> parseIntervalStrings(List<String> intStrings) {
        List<LocalInterval> result = []
        DateTimeFormatter dtf = DateTimeFormat.forPattern(_timeFormat).withZoneUTC()
        for (str in intStrings) {
            try {
                List<String> times = str.tokenize(_restDelimiter)
                if (times.size() == 2) {
                    LocalTime start = dtf.parseLocalTime(times[0]),
                        end = dtf.parseLocalTime(times[1])
                    result << new LocalInterval(start, end)
                }
                else {
                    return resultFactory.failWithMessage("weeklySchedule.error.invalidRestTimeFormat", [str])
                }
            }
            catch (e) {
                log.debug("WeeklyScheduleSpec.parseIntervalStrings: ${e.message}")
                return resultFactory.failWithMessage("weeklySchedule.error.invalidRestTimeFormat", [str])
            }
        }
        resultFactory.success(result)
    }

    protected Result<ScheduleChange> nextChangeForType(String changeType) {
        DateTime now = DateTime.now()
        Result<ScheduleChange> res = nextChangeForDateTime(now, now)
        if (res.success) {
            ScheduleChange sChange = res.payload
            if (sChange.type != changeType) {
                res = nextChangeForDateTime(sChange.when, sChange.when)
            }
        }
        res
    }
    private Result<ScheduleChange> nextChangeForDateTime(DateTime dt, DateTime initialDt) {
        List<Interval> intervals = rehydrateAsIntervals(dt)
        ScheduleChange sChange = null
        //set the closest upcoming to be impossibly far into the future as an initial value
        DateTime closestUpcoming = dt.plusWeeks(1)
        for (interval in intervals) {
            if (interval.contains(initialDt)) {
                sChange = new ScheduleChange(type:Constants.SCHEDULE_UNAVAILABLE, when:interval.end)
                break
            }
            else if (interval.isBefore(closestUpcoming) && interval.isAfter(initialDt)) {
                closestUpcoming = interval.start
            }
        }
        //If sChange is not null, then we found an interval that contains initialDt
        if (sChange) { resultFactory.success(sChange) }
        //Otherwise, we need to find the nearest upcoming interval
        else if (closestUpcoming && !intervals.isEmpty()) {
            resultFactory.success(new ScheduleChange(type:Constants.SCHEDULE_AVAILABLE, when:closestUpcoming))
        }
        //If no upcoming intervals on this day, check the next day
        else {
            //continue searching if we have not yet looked one week into the future
            if (initialDt.plusWeeks(1) != dt) {
                nextChangeForDateTime(dt.plusDays(1), initialDt)
            }
            else { resultFactory.failWithMessage("weeklySchedule.error.nextChangeNotFound") }
        }
    }
    private List<Interval> rehydrateAsIntervals(DateTime dt, boolean stitchEndOfDay=true) {
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
                log.error("WeeklySchedule.rehydrateAsIntervals: for intervals $intervalsString, invalid range: $rangeString")
            }
        }
        if (stitchEndOfDay && endOfDayInterval) {
            List<Interval> tomorrowIntervals = rehydrateAsIntervals(dt.plusDays(1), false)
            Interval startOfDayInterval = tomorrowIntervals.find { isStartOfDay(it.start) }
            if (startOfDayInterval) {
                endOfDayInterval = new Interval(endOfDayInterval.start, startOfDayInterval.end)
            }
        }
        endOfDayInterval ? intervals << endOfDayInterval : intervals
    }
    private boolean isEndOfDay(DateTime dt) { dt.plusMinutes(2).dayOfWeek != dt.dayOfWeek }
    private boolean isStartOfDay(DateTime dt) { dt.minusMinutes(2).dayOfWeek != dt.dayOfWeek }
    private String getDayOfWeek(DateTime dt) {
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

    private List<LocalInterval> cleanLocalIntervals(List<LocalInterval> intervals) {
        List<LocalInterval> sorted = intervals.sort(),
            cleaned = sorted.isEmpty() ? [] : [sorted[0]]
        int sortedLen = sorted.size()
        boolean mergedPrevious = true
        for(int i = 1; i < sortedLen; i++) {
            LocalInterval int1 = sorted[i - 1], int2 = sorted[i]
            if (int1 != int2) {
                if (int1.abuts(int2) || int1.overlaps(int2)) {
                    LocalInterval startingInt = mergedPrevious ? cleaned.pop() : int1
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
    private String dehydrateLocalIntervals(List<LocalInterval> intervals) {
        List<String> intStrings = []
        DateTimeFormatter dtf = DateTimeFormat.forPattern(_timeFormat).withZoneUTC()
        intervals.each { LocalInterval i ->
            intStrings << "${dtf.print(i.start)}${_timeDelimiter}${dtf.print(i.end)}"
        }
        intStrings.join(_rangeDelimiter)
    }
    private boolean validateIntervalsString(String str) {
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
        catch (e) { return false }
    }

    /////////////////////
    // Property Access //
    /////////////////////

    Map<String,List<LocalInterval>> getAllAsLocalIntervals(String timezone=null) {
        ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday",
            "saturday"].collectEntries { String dayOfWeek ->
            DateTimeFormatter dtf = DateTimeFormat.forPattern(_timeFormat).withZoneUTC()
            List<Interval> localIntervals = []
            this."$dayOfWeek".tokenize(_rangeDelimiter).each { String rangeString ->
                List<String> times = rangeString.tokenize(_timeDelimiter)
                if (times.size() == 2) {
                    LocalTime start = dtf.parseLocalTime(times[0])
                    LocalTime end = dtf.parseLocalTime(times[1])
                    localIntervals << new LocalInterval(start, end)
                }
                else {
                    log.error("WeeklySchedule.getAsLocalIntervals: for $dayOfWeek, invalid range: $rangeString")
                }
            }
            [(dayOfWeek):localIntervals]
        }
    }
}
