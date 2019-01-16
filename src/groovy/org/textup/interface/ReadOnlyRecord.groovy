package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.type.VoiceLanguage

@GrailsTypeChecked
interface ReadOnlyRecord {

    Long getId()
    DateTime getLastRecordActivity()
    VoiceLanguage getLanguage()
}
