package org.textup

import org.joda.time.DateTime

interface Contactable {
    Long getContactId()
    DateTime getLastRecordActivity()
    String getName()
    String getNote()
    ContactStatus getStatus()
    List<ContactNumber> getNumbers()

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
