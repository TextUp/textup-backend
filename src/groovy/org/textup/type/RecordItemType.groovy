package org.textup.type

import grails.compiler.GrailsTypeChecked
import org.textup.*

@GrailsTypeChecked
enum RecordItemType {
    CALL,
    TEXT,
    NOTE

    Class<? extends RecordItem> toClass() {
        switch(this) {
            case CALL:
                RecordCall
                break
            case TEXT:
                RecordText
                break
            case NOTE:
                RecordNote
                break
            default:
                RecordItem
        }
    }
}
