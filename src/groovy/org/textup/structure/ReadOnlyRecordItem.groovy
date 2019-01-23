package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*

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
