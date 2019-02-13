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

@TestMixin(GrailsUnitTestMixin)
class ActivityEntitySpec extends Specification {

    void "test has activity"() {
        when: "empty"
        ActivityEntity.HasActivity hasAct1 = new ActivityEntity.HasActivity()
        ActivityRecord a1 = Mock()

        then:
        hasAct1.id == null
        hasAct1.activity instanceof ActivityRecord
        hasAct1.totalCost == UsageUtils.UNIT_COST_NUMBER

        when: "set properties"
        hasAct1.activity = a1
        BigDecimal totalCost = hasAct1.totalCost

        then:
        1 * a1.getTextCost() >> 8
        1 * a1.getCallCost() >> 5
        totalCost == 8  + 5 + UsageUtils.UNIT_COST_NUMBER

        when: "cloning"
        hasAct1.id == 888
        ActivityEntity.HasActivity hasAct2 = hasAct1.clone()

        then:
        hasAct2.id == hasAct1.id
    }

    void "test organization"() {
        when: "empty"
        ActivityEntity.Organization org1 = new ActivityEntity.Organization()
        ActivityRecord a1 = Mock()

        then:
        org1.name == null
        org1.totalNumPhones == 0
        org1 instanceof ActivityEntity.HasActivity
        org1.totalCost == 0

        when: "set properties"
        org1.totalNumPhones = 8
        org1.activity = a1
        BigDecimal totalCost = org1.totalCost

        then:
        1 * a1.getTextCost() >> 8
        1 * a1.getCallCost() >> 5
        totalCost == 8 + 5 + (8 * UsageUtils.UNIT_COST_NUMBER)
    }

    void "test staff"() {
        given:
        Long pId = TestUtils.randIntegerUpTo(88)
        DateTime dt = DateTime.now()
        String str1 = TestUtils.randString()
        MockedMethod buildNumbersStringForMonth = MockedMethod.create(UsageUtils, "buildNumbersStringForMonth") {
            str1
        }

        when: "empty"
        ActivityEntity.Staff s1 = new ActivityEntity.Staff(phoneId: pId)
        s1.activity.monthObj = dt

        then:
        s1.name == null
        s1.username == null
        s1.email == null
        s1.phoneId == pId
        s1.activity instanceof ActivityRecord

        when: "set properties"
        String nums = s1.numbers

        then:
        buildNumbersStringForMonth.callCount == 1
        buildNumbersStringForMonth.allArgs[0] == [pId, dt]
        nums == str1

        cleanup:
        buildNumbersStringForMonth.restore()
    }

    void "test team"() {
        given:
        Long pId = TestUtils.randIntegerUpTo(88)
        DateTime dt = DateTime.now()
        String str1 = TestUtils.randString()
        MockedMethod buildNumbersStringForMonth = MockedMethod.create(UsageUtils, "buildNumbersStringForMonth") {
            str1
        }

        when: "empty"
        ActivityEntity.Team t1 = new ActivityEntity.Team(phoneId: pId)
        t1.activity.monthObj = dt

        then:
        t1.name == null
        t1.numStaff == 0
        t1.phoneId == pId
        t1.activity instanceof ActivityRecord

        when: "set properties"
        String nums = t1.numbers

        then:
        buildNumbersStringForMonth.callCount == 1
        buildNumbersStringForMonth.allArgs[0] == [pId, dt]
        nums == str1

        cleanup:
        buildNumbersStringForMonth.restore()
    }
}
