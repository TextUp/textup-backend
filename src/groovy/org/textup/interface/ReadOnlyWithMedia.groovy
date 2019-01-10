package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface ReadOnlyWithMedia {
    ReadOnlyMediaInfo getReadOnlyMedia()
}
