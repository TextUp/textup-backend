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

@TestFor(PhoneNumberHistory)
class PhoneNumberHistorySpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation and constraints"() {
        given:
        DateTime dt = DateTime.now()
        PhoneNumber invalidNum = PhoneNumber.create("invalid")
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        when:
        Result res = PhoneNumberHistory.tryCreate(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = PhoneNumberHistory.tryCreate(dt, invalidNum)

        then: "do not validate number as string"
        res.status == ResultStatus.CREATED
        res.payload.whenCreated == dt
        res.payload.numberAsString == null

        when:
        res = PhoneNumberHistory.tryCreate(dt, pNum1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.whenCreated == dt
        res.payload.numberAsString == pNum1.number
        res.payload.numberIfPresent.number == pNum1.number
    }

    void "test do not clean start and end times of range"() {
        given:
        DateTime start1 = DateTime.now()
        DateTime end1 = start1.plusDays(1).plusMonths(2)
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        when:
        PhoneNumberHistory nh1 = PhoneNumberHistory.tryCreate(start1, pNum1).payload

        then:
        nh1.whenCreated == start1
        nh1.endTime == null

        when:
        nh1.endTime = end1

        then:
        nh1.endTime == end1
    }

    void "test sorting"() {
        given:
        DateTime start1 = DateTime.now()
        DateTime start2 = DateTime.now().plusHours(1)
        DateTime end1 = start1.plusHours(1)
        DateTime end2 = start1.plusDays(1)
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        PhoneNumberHistory nh1 = PhoneNumberHistory.tryCreate(start1, pNum1).payload
        PhoneNumberHistory nh2 = PhoneNumberHistory.tryCreate(start1, pNum1).payload
        nh1.endTime = end1
        nh2.endTime = end2
        PhoneNumberHistory nh3 = PhoneNumberHistory.tryCreate(start2, pNum1).payload
        PhoneNumberHistory nh4 = PhoneNumberHistory.tryCreate(start2, pNum1).payload
        nh3.endTime = end1
        nh4.endTime = end2

        expect:
        [nh3, nh2, nh1, nh4].sort() == [nh1, nh2, nh3, nh4]
    }

    void "test determining if includes given month and year"() {
        given:
        DateTime start1 = DateTime.now()
        DateTime end1 = start1.plusDays(5).plusMonths(8)
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        when: "no end time"
        PhoneNumberHistory nh1 = PhoneNumberHistory.tryCreate(start1, pNum1).payload

        then:
        nh1.includes(null, null) == false
        nh1.includes(start1.minusMonths(1).monthOfYear, start1.minusMonths(1).year) == false
        nh1.includes(start1.monthOfYear, start1.year) == true
        nh1.includes(start1.plusMonths(1).monthOfYear, start1.plusMonths(1).year) == true
        nh1.includes(start1.plusYears(1).monthOfYear, start1.plusYears(1).year) == true

        when: "has end time"
        nh1.endTime = end1

        then:
        nh1.includes(null, null) == false
        nh1.includes(start1.minusMonths(1).monthOfYear, start1.minusMonths(1).year) == false
        nh1.includes(start1.monthOfYear, start1.year) == true
        nh1.includes(start1.plusMonths(1).monthOfYear, start1.plusMonths(1).year) == true
        nh1.includes(end1.minusMonths(1).monthOfYear, end1.minusMonths(1).year) == true
        nh1.includes(end1.monthOfYear, end1.year) == true
        nh1.includes(end1.plusYears(1).monthOfYear, end1.plusYears(1).year) == false
    }
}
