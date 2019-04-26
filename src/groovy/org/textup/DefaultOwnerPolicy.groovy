package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
class DefaultOwnerPolicy implements ReadOnlyOwnerPolicy {

    static final boolean DEFAULT_SEND_PREVIEW_LINK = true
    static final NotificationFrequency DEFAULT_FREQUENCY = NotificationFrequency.IMMEDIATELY
    static final NotificationLevel DEFAULT_LEVEL = NotificationLevel.ALL
    static final NotificationMethod DEFAULT_METHOD = NotificationMethod.TEXT

    private final Staff staff
    private final DefaultSchedule defaultSchedule

    static List<DefaultOwnerPolicy> createAll(Collection<Staff> staffs) {
        List<DefaultOwnerPolicy> ops = []
        staffs?.each { Staff s1 -> ops << DefaultOwnerPolicy.create(s1) }
        ops
    }

    static DefaultOwnerPolicy create(Staff s1) {
        new DefaultOwnerPolicy(s1, DefaultSchedule.create())
    }

    // Methods
    // -------

    @Override
    boolean canNotifyForAny(Collection<Long> recordIds) { true }

    @Override
    boolean isActive() { true }

    @Override
    boolean isAllowed(Long recordId) { true }

    // Properties
    // ----------

    @Override
    boolean getShouldSendPreviewLink() { DEFAULT_SEND_PREVIEW_LINK }

    @Override
    NotificationFrequency getFrequency() { DEFAULT_FREQUENCY }

    @Override
    NotificationLevel getLevel() { DEFAULT_LEVEL }

    @Override
    NotificationMethod getMethod() { DEFAULT_METHOD }

    @Override
    ReadOnlySchedule getReadOnlySchedule() { defaultSchedule }

    @Override
    ReadOnlyStaff getReadOnlyStaff() { staff }
}
