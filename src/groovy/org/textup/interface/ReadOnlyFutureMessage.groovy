package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.type.VoiceLanguage
import org.textup.type.FutureMessageType

@GrailsTypeChecked
interface ReadOnlyFutureMessage extends ReadOnlyWithMedia {
    Long getId()
    ReadOnlyRecord getReadOnlyRecord()
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
