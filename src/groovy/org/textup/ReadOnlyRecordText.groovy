package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
interface ReadOnlyRecordText extends ReadOnlyRecordItem {
    String getContents()
}
