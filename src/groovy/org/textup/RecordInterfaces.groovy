package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.type.VoiceLanguage
import org.textup.type.FutureMessageType

@GrailsTypeChecked
interface ReadOnlyRecord {

    Long getId()
    DateTime getLastRecordActivity()
    VoiceLanguage getLanguage()

    boolean hasUnreadInfo(DateTime lastTouched)
    UnreadInfo getUnreadInfo(DateTime lastTouched)

    int countFutureMessages()
    List<? extends ReadOnlyFutureMessage> getFutureMessages()
    List<? extends ReadOnlyFutureMessage> getFutureMessages(Map params)
}

@GrailsTypeChecked
interface ReadOnlyFutureMessage extends ReadOnlyWithMedia {
    Long getId()
    ReadOnlyRecord getReadOnlyRecord()
    FutureMessageType getType()

    DateTime getWhenCreated()
    DateTime getStartDate()
    DateTime getNextFireDate()
    DateTime getEndDate()

    boolean getNotifySelf()
    boolean getIsReallyDone()
    boolean getIsRepeating()

    String getMessage()
    VoiceLanguage getLanguage()
}

@GrailsTypeChecked
interface ReadOnlySimpleFutureMessage extends ReadOnlyFutureMessage {
    Integer getRepeatCount()
    long getRepeatIntervalInDays()
    Integer getTimesTriggered()
}

@GrailsTypeChecked
interface ReadOnlyRecordText {
    String getContents()
}

@GrailsTypeChecked
interface ReadOnlyRecordCall {
    int getDurationInSeconds()
    int getVoicemailInSeconds()
    boolean isStillOngoing()
}

@GrailsTypeChecked
interface ReadOnlyRecordItem extends Authorable, WithMedia {
    Long getId()
    ReadOnlyRecord getReadOnlyRecord()

    DateTime getWhenCreated()
    boolean getOutgoing()
    boolean getHasAwayMessage()
    boolean getIsAnnouncement()
    boolean getWasScheduled()
    boolean getIsDeleted()

    String getNoteContents()
    RecordItemStatus groupReceiptsByStatus()
}

// An interface that delineates the basic properties that a note or a revision
// of a note should have
@GrailsTypeChecked
interface ReadOnlyBaseRecordNote extends Authorable {
    Long getId()
    DateTime getWhenChanged()
    String getNoteContents()
    ReadOnlyLocation getReadOnlyLocation()
}

// Adds some additional properties that a RecordNote should have but child
// RecordNoteRevisions should not have
@GrailsTypeChecked
interface ReadOnlyRecordNote extends ReadOnlyBaseRecordNote {
    boolean getIsReadOnly()
    Set<? extends ReadOnlyRecordNoteRevision> getRevisions()
}

@GrailsTypeChecked
interface ReadOnlyRecordNoteRevision extends ReadOnlyBaseRecordNote, ReadOnlyWithMedia {
}
