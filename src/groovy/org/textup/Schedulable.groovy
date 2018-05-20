package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
interface Schedulable {

    boolean isAvailableNow()
    Result<Schedule> updateSchedule(Map params)

    boolean getManualSchedule()
    void setManualSchedule(boolean val)

    boolean getIsAvailable()
    void setIsAvailable(boolean val)

    Schedule getSchedule()
    void setSchedule(Schedule val)
}
