package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.joda.time.*
import org.joda.time.format.*
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
class UsageUtils {

    static final double UNIT_COST_NUMBER = 5
    static final double UNIT_COST_TEXT = 0.01
    static final double UNIT_COST_CALL = 0.015

    private static final String NO_NUMBERS = "deactivated"

    // Query helpers
    // -------------

    static String getTableName(PhoneOwnershipType type) {
        switch (type) {
            case PhoneOwnershipType.INDIVIDUAL: return "staff"
            case PhoneOwnershipType.GROUP: return "team"
            default: return ""
        }
    }

    // Building data
    // -------------

    static <T extends ActivityEntity.HasActivity> List<T> associateActivity(List<T> activityOwners,
        List<ActivityRecord> activityList) {

        if (!activityOwners || !activityList) {
            return activityOwners
        }
        List<T> clonedOwners = []
        activityOwners.each { T ha1 -> clonedOwners << ha1.clone() }
        Map<BigInteger, T> ownerMap = MapUtils.buildObjectMap(clonedOwners) { T ha1 -> ha1.id }
        activityList.each { ActivityRecord a1 ->
            ownerMap.get(a1.ownerId)?.setActivity(a1)
        }
        clonedOwners
    }

    static List<ActivityRecord> ensureMonths(List<ActivityRecord> aList) {
        // do not short circuit if aList is an empty or null because we still want to ensure
        // the proper number of months even if all months are empty
        Map<String, ActivityRecord> monthStringToActivity = MapUtils
            .buildObjectMap(aList) { ActivityRecord a1 -> a1.monthString }
        getAvailableMonthStrings().each { String monthString ->
            if (!monthStringToActivity.containsKey(monthString)) {
                ActivityRecord a1 = new ActivityRecord()
                a1.setMonthStringDirectly(monthString)
                a1.monthObj = UsageUtils.monthStringToDateTime(monthString)
                monthStringToActivity[monthString] = a1
            }
        }
        new ArrayList<ActivityRecord>(monthStringToActivity.values()).sort()
    }

    static String buildNumbersStringForMonth(Number phoneId, DateTime monthObj) {
        Phone p1 = Phones.mustFindActiveForId(phoneId)
            .logFail("buildNumbersStringForMonth")
            .payload
        Collection<String> nums = p1
            ?.buildNumbersForMonth(monthObj?.monthOfYear()?.get(), monthObj?.year()?.get())
            *.prettyPhoneNumber
        nums ? CollectionUtils.joinWithDifferentLast(nums, ", ", " and ") : NO_NUMBERS
    }

    // Display helpers
    // ---------------

    static List<String> getAvailableMonthStrings() {
        RecordItem rItem = RecordItem.first("whenCreated")
        // normalize both DateTimes to start of day and month because we are only interested
        // in month and year comparisons
        DateTime now = DateTime.now().withTimeAtStartOfDay().withDayOfMonth(1),
            dt = (rItem?.whenCreated ?: now).withTimeAtStartOfDay().withDayOfMonth(1)
        List<String> monthStrings = []
        // isEqual is for the edge cas where rItem.whenCreated is null so we default to the now
        // In this case, we still want include the "now" month
        while (dt.isBefore(now) || dt.isEqual(now)) {
            monthStrings << dateTimeToMonthString(dt)
            dt = dt.plusMonths(1)
        }
        monthStrings
    }

    static int getAvailableMonthStringIndex(DateTime dt) {
        if (!dt) {
            return -1
        }
        String currentMonthString = UsageUtils.dateTimeToMonthString(dt)
        UsageUtils
            .getAvailableMonthStrings()
            .findIndexOf { String m1 -> m1 == currentMonthString }
    }

    static String queryMonthToMonthString(String queryMonth) {
        if (!queryMonth) {
            return ""
        }
        try {
            DateTime dt = DateTimeUtils.QUERY_MONTH_FORMAT.parseDateTime(queryMonth)
            dateTimeToMonthString(dt)
        }
        catch (IllegalArgumentException e) {
            return ""
        }
    }

    static String dateTimeToTimestamp(DateTime dt) {
        if (!dt) {
            return ""
        }
        DateTimeUtils.CURRENT_TIME_FORMAT.print(dt)
    }

    static String dateTimeToMonthString(DateTime dt) {
        if (!dt) {
            return ""
        }
        DateTimeUtils.DISPLAYED_MONTH_FORMAT.print(dt)
    }

    static DateTime monthStringToDateTime(String monthString) {
        if (!monthString) {
            return null
        }
        try {
            DateTimeUtils.DISPLAYED_MONTH_FORMAT.parseDateTime(monthString)
        }
        catch (IllegalArgumentException e) {
            return null
        }
    }
}
