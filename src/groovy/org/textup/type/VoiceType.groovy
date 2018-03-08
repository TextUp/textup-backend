package org.textup.type

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum VoiceType {
    MALE,
    FEMALE

    String toTwimlValue() {
        this == MALE ? 'man' : 'woman'
    }
}
