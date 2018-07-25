package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.type.VoiceLanguage
import org.textup.type.FutureMessageType

@GrailsCompileStatic
interface ReadOnlyRecord {
    DateTime getLastRecordActivity()
    VoiceLanguage getLanguage()

    int countCallsSince(DateTime since)
    int countCallsSince(DateTime since, boolean hasVoicemail)

    int countItems()
    int countItems(Collection<Class<? extends RecordItem>> types)
    List<? extends ReadOnlyRecordItem> getItems()
    List<? extends ReadOnlyRecordItem> getItems(Map params)
    List<? extends ReadOnlyRecordItem> getItems(Collection<Class<? extends RecordItem>> types)
    List<? extends ReadOnlyRecordItem> getItems(Collection<Class<? extends RecordItem>> types, Map params)

    int countSince(DateTime since)
    int countSince(DateTime since, Collection<Class<? extends RecordItem>> types)
    List<? extends ReadOnlyRecordItem> getSince(DateTime since)
    List<? extends ReadOnlyRecordItem> getSince(DateTime since, Map params)
    List<? extends ReadOnlyRecordItem> getSince(DateTime since, Collection<Class<? extends RecordItem>> types)
    List<? extends ReadOnlyRecordItem> getSince(DateTime since, Collection<Class<? extends RecordItem>> types, Map params)

    int countBetween(DateTime start, DateTime end)
    int countBetween(DateTime start, DateTime end, Collection<Class<? extends RecordItem>> types)
    List<? extends ReadOnlyRecordItem> getBetween(DateTime start, DateTime end)
    List<? extends ReadOnlyRecordItem> getBetween(DateTime start, DateTime end, Map params)
    List<? extends ReadOnlyRecordItem> getBetween(DateTime start, DateTime end, Collection<Class<? extends RecordItem>> types)
    List<? extends ReadOnlyRecordItem> getBetween(DateTime start, DateTime end, Collection<Class<? extends RecordItem>> types, Map params)

    int countFutureMessages()
    List<? extends ReadOnlyFutureMessage> getFutureMessages()
    List<? extends ReadOnlyFutureMessage> getFutureMessages(Map params)
}

@GrailsCompileStatic
interface ReadOnlyFutureMessage {
    Long getId()
    ReadOnlyRecord getRecord()
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

@GrailsCompileStatic
interface ReadOnlySimpleFutureMessage extends ReadOnlyFutureMessage {
    Integer getRepeatCount()
    long getRepeatIntervalInDays()
    Integer getTimesTriggered()
}

@GrailsCompileStatic
interface ReadOnlyRecordText {
    String getContents()
}

@GrailsCompileStatic
interface ReadOnlyRecordCall {
    int getDurationInSeconds()
    boolean getHasVoicemail()
    String getVoicemailUrl()
    int getVoicemailInSeconds()
}

@GrailsCompileStatic
interface ReadOnlyRecordItem extends Authorable, WithMedia {
    Long getId()
    ReadOnlyRecord getRecord()

    DateTime getWhenCreated()
    boolean getOutgoing()
    boolean getHasAwayMessage()
    boolean getIsAnnouncement()

    String getNoteContents()
    RecordItemStatus groupReceiptsByStatus()
}

// An interface that delineates the basic properties that a note or a revision
// of a note should have
@GrailsCompileStatic
interface ReadOnlyBaseRecordNote extends Authorable {
    Long getId()
    DateTime getWhenChanged()
    String getNoteContents()
    ReadOnlyLocation getLocation()
}

// Adds some additional properties that a RecordNote should have but child
// RecordNoteRevisions should not have
@GrailsCompileStatic
interface ReadOnlyRecordNote extends ReadOnlyBaseRecordNote {
    boolean getIsDeleted()
    boolean getIsReadOnly()
    Set<? extends ReadOnlyRecordNoteRevision> getRevisions()
}

@GrailsCompileStatic
interface ReadOnlyRecordNoteRevision extends ReadOnlyBaseRecordNote, ReadOnlyWithMedia {
}
