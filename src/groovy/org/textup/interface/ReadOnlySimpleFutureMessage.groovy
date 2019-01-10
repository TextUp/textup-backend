package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface ReadOnlySimpleFutureMessage extends ReadOnlyFutureMessage {
    Integer getRepeatCount()
    long getRepeatIntervalInDays()
    Integer getTimesTriggered()
}
