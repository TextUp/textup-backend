package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.joda.time.*
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
interface ReadOnlySchedule {

    boolean isAvailableNow()
    DateTime nextAvailable()
    DateTime nextAvailable(String timezone)
    DateTime nextUnavailable()
    DateTime nextUnavailable(String timezone)

    Long getId()
    boolean getManual()
    boolean getManualIsAvailable()

    Map<String,List<LocalInterval>> getAllAsLocalIntervals()
    Map<String,List<LocalInterval>> getAllAsLocalIntervals(String timezone)
}
