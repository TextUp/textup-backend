package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.type.VoiceLanguage

@GrailsCompileStatic
interface ReadOnlyRecord {
    DateTime getLastRecordActivity()
    VoiceLanguage getLanguage()

    int countCallsSince(DateTime since)
    int countCallsSince(DateTime since, boolean hasVoicemail)

    int countItems()
    int countItems(Collection<Class<? extends RecordItem>> types)
    List<ReadOnlyRecordItem> getItems()
    List<ReadOnlyRecordItem> getItems(Map params)
    List<ReadOnlyRecordItem> getItems(Collection<Class<? extends RecordItem>> types)
    List<ReadOnlyRecordItem> getItems(Collection<Class<? extends RecordItem>> types, Map params)

    int countSince(DateTime since)
    int countSince(DateTime since, Collection<Class<? extends RecordItem>> types)
    List<ReadOnlyRecordItem> getSince(DateTime since)
    List<ReadOnlyRecordItem> getSince(DateTime since, Map params)
    List<ReadOnlyRecordItem> getSince(DateTime since, Collection<Class<? extends RecordItem>> types)
    List<ReadOnlyRecordItem> getSince(DateTime since, Collection<Class<? extends RecordItem>> types, Map params)

    int countBetween(DateTime start, DateTime end)
    int countBetween(DateTime start, DateTime end, Collection<Class<? extends RecordItem>> types)
    List<ReadOnlyRecordItem> getBetween(DateTime start, DateTime end)
    List<ReadOnlyRecordItem> getBetween(DateTime start, DateTime end, Map params)
    List<ReadOnlyRecordItem> getBetween(DateTime start, DateTime end, Collection<Class<? extends RecordItem>> types)
    List<ReadOnlyRecordItem> getBetween(DateTime start, DateTime end, Collection<Class<? extends RecordItem>> types, Map params)

    int countFutureMessages()
    List<ReadOnlyFutureMessage> getFutureMessages()
    List<ReadOnlyFutureMessage> getFutureMessages(Map params)
}
