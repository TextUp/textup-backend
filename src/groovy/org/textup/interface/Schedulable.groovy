package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface Schedulable extends Saveable {

    boolean isAvailableNow()
    // Result<Schedule> updateSchedule(Map params) // TODO remove?

    boolean getManualSchedule()
    void setManualSchedule(boolean val)

    boolean getIsAvailable()
    void setIsAvailable(boolean val)

    Schedule getSchedule()
    void setSchedule(Schedule val)
}
