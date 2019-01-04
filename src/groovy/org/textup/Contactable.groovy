package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.rest.NotificationStatus
import org.textup.type.ContactStatus
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
interface Contactable extends WithRecord, WithName {
    Long getContactId()
    String getName()
    String getNote()
    PhoneNumber getFromNum()
    String getCustomAccountId()
    List<ContactNumber> getNumbers()
    List<ContactNumber> getSortedNumbers()
    List<NotificationStatus> getNotificationStatuses()

    ContactStatus getStatus()
    void setStatus(ContactStatus val)

    DateTime getLastTouched()
    void setLastTouched(DateTime val)

    boolean instanceOf(Class clazz)
}
