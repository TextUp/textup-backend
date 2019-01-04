package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode

@GrailsTypeChecked
@EqualsAndHashCode
class PhoneRecord {

    Phone phone
    Record record

    static constraints = { // default nullable: false
        record cascadeValidation: true
    }
    static mapping = {
        record lazy: false, cascade: "save-update"
    }

    // Overrides
    // ---------

    def beforeValidate() {
        if (!record) {
            record = new Record()
        }
    }

    // Methods
    // -------

    List<NotificationStatus> getNotificationStatuses() {
        phone.owner.getNotificationStatusesForRecords([record.id])
    }
}
