package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlySimpleFutureMessage extends ReadOnlyFutureMessage {
    Integer getRepeatCount()
    long getRepeatIntervalInDays()
    Integer getTimesTriggered()
}
