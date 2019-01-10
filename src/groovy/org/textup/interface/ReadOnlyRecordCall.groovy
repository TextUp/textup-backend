package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface ReadOnlyRecordCall {
    int getDurationInSeconds()
    int getVoicemailInSeconds()
}
