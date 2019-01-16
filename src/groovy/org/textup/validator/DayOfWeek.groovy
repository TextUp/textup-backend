package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.joda.time.*

@Sortable
@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class DayOfWeek implements Validateable {

    DayType type
    DayOfWeek prevDay
    DayOfWeek nextDay
    LocalDay today

    static constraints = {
        nextDay cascadeValidation: true
        today cascadeValidation: true
    }

    // Methods
    // -------

    def tryAddInterval(LocalTime start, LocalTime end, int adjustmentInMinutes) {
        ResultGroup<LocalInterval> resGroup = new ResultGroup<>()

        LocalTime adjustedStart = start.plusMinutes(adjustmentInMinutes),
            adjustedEnd = end.plusMinutes(adjustmentInMinutes)
        if (ScheduleUtils.isOverhangPrevious(adjustedStart, adjustedEnd, adjustmentInMinutes)) {
            resGroup << prevDay.tryAddInterval(adjustedStart, LocalTime.MIDNIGHT)
            resGroup << today.tryAddInterval(LocalTime.MIDNIGHT, adjustedEnd)
        }
        else if (ScheduleUtils.isOverhangNext(adjustedStart, adjustedEnd, adjustmentInMinutes)) {
            resGroup << today.tryAddInterval(adjustedStart, LocalTime.MIDNIGHT)
            resGroup << nextDay.tryAddInterval(LocalTime.MIDNIGHT, adjustedEnd)
        }
        else { resGroup << today.tryAddInterval(adjustedStart, adjustedEnd) }
        resGroup
    }
}
