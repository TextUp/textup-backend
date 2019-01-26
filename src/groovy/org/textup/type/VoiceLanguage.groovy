package org.textup.type

import com.twilio.twiml.Say.Language
import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum VoiceLanguage {
    CHINESE(Language.ZH_CN),
    ENGLISH(Language.EN_US),
    FRENCH(Language.FR_CA),
    GERMAN(Language.DE_DE),
    ITALIAN(Language.IT_IT),
    JAPANESE(Language.JA_JP),
    KOREAN(Language.KO_KR),
    PORTUGUESE(Language.PT_BR),
    RUSSIAN(Language.RU_RU),
    SPANISH(Language.ES_MX)

    private final Language twilioLanguage

    VoiceLanguage(Language lang) {
        twilioLanguage = lang
    }

    String toTwimlValue() {
        twilioLanguage.toString()
    }
}
