package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.rest.NotificationStatus
import org.textup.type.ContactStatus
import org.textup.type.VoiceLanguage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsCompileStatic
interface Contactable {
    Long getContactId()
    DateTime getLastRecordActivity()
    String getName()
    String getNote()
    PhoneNumber getFromNum()
    List<ContactNumber> getNumbers()
    List<ContactNumber> getSortedNumbers()
    Record getRecord()

    ContactStatus getStatus()
    void setStatus(ContactStatus val)

    DateTime getLastTouched()
    void setLastTouched(DateTime val)

    VoiceLanguage getLanguage()
    void setLanguage(VoiceLanguage lang)

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

    List<NotificationStatus> getNotificationStatuses()

    Result<RecordText> storeOutgoingText(String message, TempRecordReceipt receipt)
    Result<RecordText> storeOutgoingText(String message, TempRecordReceipt receipt, Staff staff)
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt)
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt, Staff staff)
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt, Staff staff, String message)

    boolean instanceOf(Class clazz)
}
