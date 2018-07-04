package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.rest.NotificationStatus
import org.textup.type.ContactStatus
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsCompileStatic
interface Contactable {
    Long getContactId()
    String getName()
    String getNote()
    PhoneNumber getFromNum()
    List<ContactNumber> getNumbers()
    List<ContactNumber> getSortedNumbers()
    List<NotificationStatus> getNotificationStatuses()

    Record getRecord()
    ReadOnlyRecord getReadOnlyRecord()

    ContactStatus getStatus()
    void setStatus(ContactStatus val)

    DateTime getLastTouched()
    void setLastTouched(DateTime val)

    Result<RecordText> storeOutgoingText(String message, TempRecordReceipt receipt)
    Result<RecordText> storeOutgoingText(String message, TempRecordReceipt receipt, Staff staff)
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt)
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt, Staff staff)
    Result<RecordCall> storeOutgoingCall(TempRecordReceipt receipt, Staff staff, String message)

    boolean instanceOf(Class clazz)
}
