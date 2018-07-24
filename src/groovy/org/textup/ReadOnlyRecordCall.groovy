package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
interface ReadOnlyRecordCall {
    int getDurationInSeconds()
    boolean getHasVoicemail()
    String getVoicemailUrl()
    int getVoicemailInSeconds()
}
