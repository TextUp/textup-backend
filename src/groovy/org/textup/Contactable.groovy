package org.textup

import org.joda.time.DateTime

interface Contactable {

    Long getContactId()

    DateTime getLastRecordActivity() 
    void updateLastRecordActivity()

	Result<RecordResult> call(Staff staffMakingCall, Map params)
    Result<RecordResult> call(Staff staffMakingCall, Map params, Author author)
    Result<RecordResult> text(Map params)
    Result<RecordResult> text(Map params, Author author)
    Result<RecordResult> addNote(Map params)
    Result<RecordResult> addNote(Map params, Author author)
    Result<RecordResult> editNote(long noteId, Map params)
    Result<RecordResult> editNote(long noteId, Map params, Author author)
    Author getAuthor()

    Result<PhoneNumber> mergeNumber(String number, Map params)
    List<PhoneNumber> getNumbers()
    Result deleteNumber(String number)

    List<RecordItem> getItems()
    List<RecordItem> getItems(Map params)
    int countItems()
    
    List<RecordItem> getSince(DateTime since)
    List<RecordItem> getSince(DateTime since, Map params)
    int countSince(DateTime since)

    List<RecordItem> getBetween(DateTime start, DateTime end)
    List<RecordItem> getBetween(DateTime start, DateTime end, Map params)
    int countBetween(DateTime start, DateTime end)
}