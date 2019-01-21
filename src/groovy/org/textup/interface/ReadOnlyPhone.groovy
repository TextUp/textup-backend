package org.textup.interface

import grails.compiler.GrailsTypeChecked

// TODO finish after updating marshallers

@GrailsTypeChecked
interface ReadOnlyPhone {

    boolean getUseVoicemailRecordingIfPresent()
    Long getId()
    PhoneNumber getNumber()
    ReadOnlyMediaInfo getMedia()
    String getAwayMessage()
    String getName()
    VoiceLanguage getLanguage()
    VoiceType getVoice()
}
