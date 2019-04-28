package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyRecord {

    Long getId()
    DateTime getLastRecordActivity()
    VoiceLanguage getLanguage()
}
