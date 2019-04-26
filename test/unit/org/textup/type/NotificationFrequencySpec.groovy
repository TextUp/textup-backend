package org.textup.type

import org.joda.time.*
import org.textup.test.*
import spock.lang.*

class NotificationFrequencySpec extends Specification {

    void "test building time obj in past"() {
        given:
        DateTime dt = DateTime.now()

        expect: "date time past is within one minute of advertised `minutesInPast`"
        NotificationFrequency.IMMEDIATELY.minutesInPast -
            Minutes.minutesBetween(NotificationFrequency.IMMEDIATELY.buildDateTimeInPast(), dt).value  <= 1
        NotificationFrequency.QUARTER_HOUR.minutesInPast -
            Minutes.minutesBetween(NotificationFrequency.QUARTER_HOUR.buildDateTimeInPast(), dt).value  <= 1
        NotificationFrequency.HALF_HOUR.minutesInPast -
            Minutes.minutesBetween(NotificationFrequency.HALF_HOUR.buildDateTimeInPast(), dt).value  <= 1
        NotificationFrequency.HOUR.minutesInPast -
            Minutes.minutesBetween(NotificationFrequency.HOUR.buildDateTimeInPast(), dt).value  <= 1
    }

    void "test getting readable description will fallback if absent"() {
        expect:
        NotificationFrequency.descriptionWithFallback(null) == NotificationFrequency.IMMEDIATELY.readableDescription
        NotificationFrequency.descriptionWithFallback(NotificationFrequency.HALF_HOUR) == NotificationFrequency.HALF_HOUR.readableDescription
    }
}
