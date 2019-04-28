package org.textup.type

import spock.lang.*
import org.textup.test.*

class StaffStatusSpec extends Specification {

    void "test boolean flags"() {
        expect:
        StaffStatus.BLOCKED.isPending() == false
        StaffStatus.BLOCKED.isActive() == false
        StaffStatus.PENDING.isPending() == true
        StaffStatus.PENDING.isActive() == false
        StaffStatus.STAFF.isPending() == false
        StaffStatus.STAFF.isActive() == true
        StaffStatus.ADMIN.isPending() == false
        StaffStatus.ADMIN.isActive() == true
    }
}
