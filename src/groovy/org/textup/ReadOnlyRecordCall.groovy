package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
interface ReadOnlyRecordCall extends ReadOnlyRecordItem {
    int getDurationInSeconds()
    boolean getHasVoicemail()
    String getVoicemailUrl()
    int getVoicemailInSeconds()
    String getCallContents()
}
