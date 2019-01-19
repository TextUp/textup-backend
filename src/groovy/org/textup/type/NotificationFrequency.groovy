package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum NotificationFrequency {
    IMMEDIATELY("few moments"),
    QUARTER_HOUR("fifteen minutes")
    HALF_HOUR("half hour"),
    HOUR("hour")

    final String readableDescription // goes after "In the last..." in mail template

    NotificationFrequency(String desc) {
        readableDescription = desc
    }
}
