package org.textup.interface

import grails.compiler.GrailsTypeChecked

// TODO finish after updating marshallers

@GrailsTypeChecked
interface ReadOnlyPhone {

    BasePhoneNumber getNumber()
    boolean getUseVoicemailRecordingIfPresent()
    Long getId()
    ReadOnlyMediaInfo getMedia()
    String getAwayMessage()
    VoiceLanguage getLanguage()
    VoiceType getVoice()
}
