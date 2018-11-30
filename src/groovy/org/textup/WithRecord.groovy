package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface WithRecord {
    Result<Record> tryGetRecord()
    Result<ReadOnlyRecord> tryGetReadOnlyRecord()
}
