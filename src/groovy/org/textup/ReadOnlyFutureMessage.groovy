package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.type.FutureMessageType
import org.textup.type.VoiceLanguage

@GrailsCompileStatic
interface ReadOnlyFutureMessage {
    Long getId()
    ReadOnlyRecord getRecord()
    FutureMessageType getType()

    DateTime getWhenCreated()
    DateTime getStartDate()
    DateTime getNextFireDate()
    DateTime getEndDate()

    boolean getNotifySelf()
    boolean getIsReallyDone()
    boolean getIsRepeating()

    String getMessage()
    VoiceLanguage getLanguage()
}
