package org.textup.type

import com.twilio.twiml.Say.Voice
import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum VoiceType {
    MALE(Voice.MAN),
    FEMALE(Voice.WOMAN)

    private final Voice twilioVoice

    VoiceType(Voice voice) {
        this.twilioVoice = voice
    }

    String toTwimlValue() {
        this.twilioVoice.toString()
    }
}
