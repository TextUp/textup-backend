package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyOwnerPolicy {

    boolean canNotifyForAny(Collection<Long> recordIds)
    boolean isAllowed(Long recordId)

    boolean getShouldSendPreviewLink()
    NotificationFrequency getFrequency()
    NotificationLevel getLevel()
    NotificationMethod getMethod()
    ReadOnlySchedule getReadOnlySchedule()
    ReadOnlyStaff getReadOnlyStaff()
}
