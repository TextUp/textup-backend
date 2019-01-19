package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.RecordItemReceiptInfo

@GrailsTypeChecked
interface ReadOnlyRecordItem extends Authorable, WithMedia {
    Long getId()
    ReadOnlyRecord getReadOnlyRecord()

    DateTime getWhenCreated()
    boolean getOutgoing()
    boolean getHasAwayMessage()
    boolean getIsAnnouncement()
    boolean getWasScheduled()

    String getNoteContents()
    RecordItemReceiptInfo groupReceiptsByStatus()
}
