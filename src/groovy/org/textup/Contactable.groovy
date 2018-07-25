package org.textup

import grails.compiler.GrailsCompileStatic
import org.joda.time.DateTime
import org.textup.rest.NotificationStatus
import org.textup.type.ContactStatus
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsCompileStatic
interface Contactable extends WithRecord {
    Long getContactId()
    String getName()
    String getNote()
    PhoneNumber getFromNum()
    List<ContactNumber> getNumbers()
    List<ContactNumber> getSortedNumbers()
    List<NotificationStatus> getNotificationStatuses()

    // see WithRecord for `tryGetRecord()`
    Result<ReadOnlyRecord> tryGetReadOnlyRecord()

    ContactStatus getStatus()
    void setStatus(ContactStatus val)

    DateTime getLastTouched()
    void setLastTouched(DateTime val)

    boolean instanceOf(Class clazz)
}
