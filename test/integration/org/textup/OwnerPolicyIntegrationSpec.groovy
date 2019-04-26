package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

// Must test in integration test because mocking error in Grails prevents creation
// of addTo* and removeFrom* in certain cases in unit tests
// see: https://github.com/grails/grails-datastore-test-support/issues/1

class OwnerPolicyIntegrationSpec extends Specification {

    void "test enabling and disabling specific record ids"() {
        given:
        Long recId1 = 22
        Long recId2 = 88
        Long recId3 = 99
        Phone p1 = TestUtils.buildStaffPhone()
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = OwnerPolicy.tryCreate(p1.owner, s1.id)

        then:
        res.payload.isAllowed(recId1) == true
        res.payload.isAllowed(recId2) == true
        res.payload.isAllowed(recId3) == true

        when: "level is ALL = use blacklist"
        res.payload.level = NotificationLevel.ALL
        res.payload.enable(recId1)
        res.payload.disable(recId2)

        then:
        res.payload.isAllowed(recId1) == true
        res.payload.isAllowed(recId2) == false
        res.payload.isAllowed(recId3) == true

        when: "level is NONE = use whitelist"
        res.payload.level = NotificationLevel.NONE

        then:
        res.payload.isAllowed(recId1) == true
        res.payload.isAllowed(recId2) == false
        res.payload.isAllowed(recId3) == false
    }

    void "test if can notify for any passed-in record ids"() {
        given:
        Long recId1 = 22
        Long recId2 = 88
        Phone p1 = TestUtils.buildStaffPhone()
        Staff s1 = TestUtils.buildStaff()
        OwnerPolicy.withSession { it.flush() }

        OwnerPolicy op1 = OwnerPolicy.tryCreate(p1.owner, s1.id).payload
        op1.enable(recId1)
        op1.disable(recId2)

        when: "available"
        op1.schedule.with {
            manual = true
            manualIsAvailable = true
        }

        then:
        op1.isActive()
        op1.canNotifyForAny(null) == false
        op1.canNotifyForAny([]) == false
        op1.canNotifyForAny([recId1]) == true
        op1.canNotifyForAny([recId2]) == false
        op1.canNotifyForAny([recId1, recId2]) == true

        when: "not available"
        op1.schedule.manualIsAvailable = false

        then:
        op1.isActive() == false
        op1.canNotifyForAny(null) == false
        op1.canNotifyForAny([]) == false
        op1.canNotifyForAny([recId1]) == false
        op1.canNotifyForAny([recId2]) == false
        op1.canNotifyForAny([recId1, recId2]) == false
    }
}
