package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.type.VoiceLanguage

@GrailsTypeChecked
interface ReadOnlyRecord {

    Long getId()
    DateTime getLastRecordActivity()
    VoiceLanguage getLanguage()

    boolean hasUnreadInfo(DateTime lastTouched)
    UnreadInfo getUnreadInfo(DateTime lastTouched)

    int countFutureMessages()
    List<? extends ReadOnlyFutureMessage> getFutureMessages()
    List<? extends ReadOnlyFutureMessage> getFutureMessages(Map params)
}
