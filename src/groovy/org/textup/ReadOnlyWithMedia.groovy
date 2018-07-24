package org.textup

import grails.compiler.GrailsCompileStatic

// Implementation notes
// --------------------
//
// Only the `serializedMedia` property is persisted. The `media` property should be transient.
// The SQL type of the `serializedMedia` property should be 'text'.

@GrailsCompileStatic
interface ReadOnlyWithMedia {

    String getSerializedMedia()
    MediaInfo getMedia()
}
