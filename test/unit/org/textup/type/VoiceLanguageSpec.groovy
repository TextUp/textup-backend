package org.textup.type

import org.textup.test.*
import spock.lang.*

class VoiceLanguageSpec extends Specification {

    void "test conversion to Twiml value string"() {
        expect:
        VoiceLanguage.values().every { it.toTwimlValue() instanceof String }
    }
}
