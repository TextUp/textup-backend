package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime

@GrailsCompileStatic
interface ReadOnlyRecordItem extends Authorable {
    Long getId()
    ReadOnlyRecord getRecord()

    DateTime getWhenCreated()
    boolean getOutgoing()
    boolean getHasAwayMessage()
    boolean getIsAnnouncement()
    Set<ReadOnlyRecordItemReceipt> getReceipts()
}
