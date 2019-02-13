package org.textup.type

import org.textup.test.*
import spock.lang.*

class VoiceTypeSpec extends Specification {

    void "test conversion to Twiml value string"() {
        expect:
        VoiceType.values().every { it.toTwimlValue() instanceof String }
    }
}
