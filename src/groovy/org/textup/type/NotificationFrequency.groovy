package org.textup.type

import grails.compiler.GrailsTypeChecked
import java.util.concurrent.TimeUnit
import org.joda.time.DateTime

@GrailsTypeChecked
enum NotificationFrequency {
    IMMEDIATELY(0, "few moments"),
    QUARTER_HOUR(15, "fifteen minutes"),
    HALF_HOUR(30, "half hour"),
    HOUR(60, "hour")

    final int minutesInPast
    final String readableDescription // goes after "In the last..." in mail template

    NotificationFrequency(int numMinutes, String desc) {
        minutesInPast = numMinutes
        readableDescription = desc
    }

    DateTime buildDateTimeInPast() {
        DateTime.now().minus(TimeUnit.MINUTES.toMillis(minutesInPast))
    }
}
