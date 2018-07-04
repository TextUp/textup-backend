package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
interface ReadOnlySimpleFutureMessage extends ReadOnlyFutureMessage {
    Integer getRepeatCount()
    long getRepeatIntervalInDays()
    Integer getTimesTriggered()
}
