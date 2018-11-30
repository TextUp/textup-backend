package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class RecordItemRequestSection {
    String phoneName = ""
    Collection<String> contactNames = []
    Collection<String> tagNames = []
    Collection<String> sharedContactNames = []

    Collection<? extends ReadOnlyRecordItem> recordItems = []
}
