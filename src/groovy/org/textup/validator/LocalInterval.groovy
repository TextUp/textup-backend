package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.joda.time.Interval
import org.joda.time.LocalTime
import org.joda.time.Minutes
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class LocalInterval implements Comparable<LocalInterval>, CanValidate {

    final LocalTime start
    final LocalTime end

    LocalInterval() {}
    LocalInterval(Map m) {
        start = m.start as LocalTime
        end = m.end as LocalTime
    }
    LocalInterval(LocalTime s, LocalTime e) {
        start = s
        end = e
    }
    LocalInterval(Interval i) {
        start = i.start.toLocalTime()
        end = i.end.toLocalTime()
    }

    static constraints = {
        start nullable:false, validator:{ LocalTime s, LocalInterval obj ->
            if (s.isAfter(obj.end)) { ["invalid"] }
        }
        end nullable:false
    }

    // Methods
    // -------

    boolean abuts(LocalInterval i) {
        !overlaps(i) && (end?.isEqual(i?.start) || start?.isEqual(i?.end))
    }

    boolean overlaps(LocalInterval i) {
        LocalTime s1 = start, e1 = end,
            s2 = i.start, e2 = i.end
        if ([s1, e1, s2, e2].any { it == null }) return false
        (s1.isBefore(s2) && e1.isAfter(s2) && e1.isBefore(e2)) ||
            (e1.isAfter(e2) && s1.isBefore(e2) && s1.isAfter(s2)) ||
            s1.isEqual(s2) || e1.isEqual(e2) ||
            (s1.isAfter(s2) && e1.isBefore(e2)) ||
            (s1.isBefore(s2) && e1.isAfter(e2))
    }

    boolean withinMinutesOf(LocalInterval i, int minutesThreshold) {
        LocalTime s1 = start, e1 = end,
            s2 = i.start, e2 = i.end
        int minBetween1 = Math.abs(Minutes.minutesBetween(e1, s2).minutes),
            minBetween2 = Math.abs(Minutes.minutesBetween(e2, s1).minutes)
        !overlaps(i) && (minBetween1 <= minutesThreshold || minBetween2 <= minutesThreshold)
    }

    @Override
    int compareTo(LocalInterval i) {
        start <=> i.start ?: end <=> i.end
    }

    @Override
    String toString() {
        if (start && end) {
            String start1 = start.hourOfDay.toString().padLeft(2, "0"),
                start2 = start.minuteOfHour.toString().padLeft(2, "0"),
                start = "${start1}${start2}"
            String end1 = end.hourOfDay.toString().padLeft(2, "0"),
                end2 = end.minuteOfHour.toString().padLeft(2, "0"),
                end = "${end1}${end2}"
            "${start}:${end}"
        }
        else { "" }
    }
}
