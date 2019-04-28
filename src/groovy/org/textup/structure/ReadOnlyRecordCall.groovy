package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyRecordCall {
    int getDurationInSeconds()
    int getVoicemailInSeconds()
}
