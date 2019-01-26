package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.TupleConstructor
import groovy.util.logging.Log4j
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@Log4j
@ToString
@TupleConstructor(includeFields = true)
@Validateable
class ScheduleChange implements CanValidate {

    final ScheduleStatus type
    final DateTime when
    final String timezone
    private final DateTimeZone tz

    static constraints = {
    	timezone blank: true, nullable: true
    	tz nullable: true
    }

    static Result<ScheduleChange> tryCreate(ScheduleStatus type, DateTime when, String timezone) {
        DateTimeZone tz = JodaUtils.getZoneFromId(timezone)
        ScheduleChange sChange1 = new ScheduleChange(type, when, timezone, tz)
        DomainUtils.tryValidate(sChange1, ResultStatus.CREATED)
    }

    // Properties
    // ----------

    DateTime getWhen() { tz ? when?.withZone(tz) : when }
}
