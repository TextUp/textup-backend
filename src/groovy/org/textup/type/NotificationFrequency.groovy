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
    // Goes after "In the last..." in mail template
    // See `NotificationUtils.buildTextMessage` for hwo this is used in text notification
    final String readableDescription

    NotificationFrequency(int numMinutes, String desc) {
        minutesInPast = numMinutes
        readableDescription = desc
    }

    DateTime buildDateTimeInPast() {
        DateTime.now().minus(TimeUnit.MINUTES.toMillis(minutesInPast))
    }

    static String descriptionWithFallback(NotificationFrequency freq1) {
        (freq1 ?: NotificationFrequency.IMMEDIATELY).readableDescription
    }
}
