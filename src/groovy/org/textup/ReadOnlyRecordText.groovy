package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
interface ReadOnlyRecordText {
    String getContents()
}
