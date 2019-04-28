package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyRecordItem extends Authorable, WithMedia {
    Long getId()
    ReadOnlyRecord getReadOnlyRecord()

    boolean getHasAwayMessage()
    boolean getIsAnnouncement()
    boolean getIsDeleted()
    boolean getOutgoing()
    boolean getWasScheduled()
    DateTime getWhenCreated()

    String getNoteContents()
    RecordItemReceiptInfo groupReceiptsByStatus()
}
