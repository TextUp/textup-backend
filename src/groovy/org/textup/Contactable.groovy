package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.types.ContactStatus
import org.textup.validator.TempRecordReceipt

@GrailsCompileStatic
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

    List<FutureMessage> getFutureMessages()
    List<FutureMessage> getFutureMessages(Map params)
    int countFutureMessages()

    Result<RecordText> storeOutgoingText(String message, TempRecordReceipt receipt, Staff staff)
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt, Staff staff)

    boolean instanceOf(Class clazz)
}
