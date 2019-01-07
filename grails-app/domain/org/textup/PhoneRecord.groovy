package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime

@GrailsTypeChecked
@EqualsAndHashCode
class PhoneRecord {

    Phone phone
    Record record
    DateTime dateExpired // active if dateExpired is null or in the future

    static constraints = { // default nullable: false
        record cascadeValidation: true
        dateExpired nullable: true
    }
    static mapping = {
        dateExpired type: PersistentDateTime
        record fetch: "join", cascade: "save-update"
    }

    def beforeValidate() {
        if (!record) {
            record = new Record()
        }
    }

    boolean isExpired() {
        dateExpired?.isBeforeNow()
    }

    // Properties
    // ----------


}
