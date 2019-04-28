package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.*
import org.textup.structure.*
import org.textup.validator.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
class DefaultSchedule implements ReadOnlySchedule {

    static final boolean DEFAULT_MANUAL = true
    static final boolean DEFAULT_MANUAL_IS_AVAILABLE = true

    static DefaultSchedule create() { new DefaultSchedule() }

    // Methods
    // -------

    @Override
    boolean isAvailableNow() { DEFAULT_MANUAL && DEFAULT_MANUAL_IS_AVAILABLE }

    @Override
    DateTime nextAvailable(String timezone = null) { null }

    @Override
    DateTime nextUnavailable(String timezone = null) { null }

    // Properties
    // ----------

    @Override
    Long getId() { null }

    @Override
    boolean getManual() { DEFAULT_MANUAL }

    @Override
    boolean getManualIsAvailable() { DEFAULT_MANUAL_IS_AVAILABLE }

    @Override
    Map<String, List<LocalInterval>> getAllAsLocalIntervals(String timezone = null) {
        ScheduleUtils.DAYS_OF_WEEK.collectEntries { String day -> [(day): []] }
    }
}
