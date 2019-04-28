package org.textup.type

import spock.lang.*
import org.textup.test.*
import static org.textup.type.PhoneRecordStatus.*

class PhoneRecordStatusSpec extends Specification {

    void "test reconciling statuses to most permissive"() {
        expect:
        PhoneRecordStatus.reconcile(null) == BLOCKED
        PhoneRecordStatus.reconcile([]) == BLOCKED
        PhoneRecordStatus.reconcile([BLOCKED]) == BLOCKED
        PhoneRecordStatus.reconcile([BLOCKED, ARCHIVED]) == ARCHIVED
        PhoneRecordStatus.reconcile([BLOCKED, ARCHIVED, ACTIVE]) == ACTIVE
        PhoneRecordStatus.reconcile([BLOCKED, ARCHIVED, ACTIVE, UNREAD]) == ACTIVE
        PhoneRecordStatus.reconcile([UNREAD]) == ACTIVE
    }
}
