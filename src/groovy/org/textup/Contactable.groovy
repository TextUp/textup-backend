package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.springframework.validation.Errors
import org.textup.rest.NotificationStatus
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
interface Contactable extends WithRecord, WithName {
    Long getContactId()
    String getSecureName()
    String getPublicName()
    String getNote()
    PhoneNumber getFromNum()
    String getCustomAccountId()
    List<ContactNumber> getSortedNumbers()
    List<NotificationStatus> getNotificationStatuses()

    ContactStatus getStatus()
    void setStatus(ContactStatus val)

    DateTime getLastTouched()
    void setLastTouched(DateTime val)

    boolean instanceOf(Class clazz)

    Contactable save()
    Errors getErrors()
}
