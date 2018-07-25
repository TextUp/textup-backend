package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
interface WithRecord {
    Result<Record> tryGetRecord()
}
