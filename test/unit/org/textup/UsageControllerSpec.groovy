package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import javax.servlet.http.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestFor(UsageController)
class UsageControllerSpec extends Specification {

    void "test building usage and costs"() {
        given:
        ActivityEntity.HasActivity a1 = Mock()
        ActivityRecord activity1 = Mock()

        when:
        Map info = controller.buildUsageAndCosts([a1])

        then:
        1 * a1.totalCost >> TestUtils.randIntegerUpTo(888) + 1
        (1.._) * a1.activity >> activity1
        1 * activity1.cost >> TestUtils.randIntegerUpTo(888) + 1
        1 * activity1.textCost >> TestUtils.randIntegerUpTo(888) + 1
        1 * activity1.callCost >> TestUtils.randIntegerUpTo(888) + 1
        1 * activity1.numTexts >> TestUtils.randIntegerUpTo(888) + 1
        1 * activity1.numSegments >> TestUtils.randIntegerUpTo(888) + 1
        1 * activity1.numCalls >> TestUtils.randIntegerUpTo(888) + 1
        1 * activity1.numMinutes >> TestUtils.randIntegerUpTo(888) + 1
        1 * activity1.numBillableMinutes >> TestUtils.randIntegerUpTo(888) + 1
        info.totalCost > 0
        info.usageCost > 0
        info.textCost > 0
        info.callCost > 0
        info.numTexts > 0
        info.numSegments > 0
        info.numCalls > 0
        info.numMinutes > 0
        info.numBillableMinutes > 0
    }

    void "test building phone counts"() {
        given:
        ActivityEntity.Organization org1 = Mock()
        ActivityRecord activity1 = Mock()

        when:
        Map info = controller.buildPhoneCounts([org1])

        then:
        1 * org1.totalNumPhones >> TestUtils.randIntegerUpTo(888) + 1
        (1.._) * org1.activity >> activity1
        1 * activity1.numActivePhones >> TestUtils.randIntegerUpTo(888) + 1
        info.numPhones > 0
        info.numActivePhones > 0

        when:
        int numPhones = TestUtils.randIntegerUpTo(888)
        int numActivePhones = TestUtils.randIntegerUpTo(888)
        info = controller.buildPhoneCounts(numPhones, numActivePhones)

        then:
        info.numPhones == numPhones
        info.numActivePhones == numActivePhones
    }

    @DirtiesRuntime
    void "test building timeframe parameters"() {
        given:
        MockedMethod dateTimeToMonthString = MockedMethod.create(UsageUtils, "dateTimeToMonthString") { "hi" }
        MockedMethod getAvailableMonthStrings = MockedMethod.create(UsageUtils, "getAvailableMonthStrings") { "hi" }
        MockedMethod dateTimeToTimestamp = MockedMethod.create(UsageUtils, "dateTimeToTimestamp") { "hi" }

        when:
        Map info = controller.buildTimeframeParams(null)

        then:
        dateTimeToMonthString.callCount == 1
        getAvailableMonthStrings.callCount == 1
        dateTimeToTimestamp.callCount == 1

        info.monthString != null
        info.availableMonthStrings != null
        info.currentTime != null
    }

    void "test getting timeframe from session with fallback"() {
        given:
        DateTime dt = DateTime.now().minusMonths(8)

        when: "no month key stored on session"
        DateTime storedDt = controller.getTimeframe(session)

        then: "revert to fallback value"
        session[controller.SESSION_MONTH_KEY] == null
        storedDt != null

        when: "has month key stored on session"
        session.setAttribute(controller.SESSION_MONTH_KEY, UsageUtils.dateTimeToMonthString(dt))
        storedDt = controller.getTimeframe(session)

        then:
        session[controller.SESSION_MONTH_KEY] != null
        storedDt == dt.withTimeAtStartOfDay().withDayOfMonth(1)
    }

    void "test updating timeframe action"() {
        given:
        String monthKey1 = TestUtils.randString()
        String monthKey2 = TestUtils.randString()
        Long orgId = TestUtils.randIntegerUpTo(88)

        when: "no org id path param"
        params.timeframe = monthKey1
        controller.updateTimeframe()

        then:
        session[controller.SESSION_MONTH_KEY] == monthKey1
        response.redirectedUrl == "/usage/index"

        when: "has org id path param"
        response.reset()

        params.timeframe = monthKey2
        params.id = orgId
        controller.updateTimeframe()

        then:
        session[controller.SESSION_MONTH_KEY] == monthKey2
        response.redirectedUrl == "/usage/show/${orgId}"
    }

    @DirtiesRuntime
    void "test getting longitudinal activity action"() {
        given:
        controller.usageService = Mock(UsageService)
        MockedMethod getAvailableMonthStringIndex = MockedMethod.create(UsageUtils, "getAvailableMonthStringIndex") { 8 }

        Long orgId = TestUtils.randIntegerUpTo(88) + 1
        String number = TestUtils.randString()

        when: "no query params"
        controller.ajaxGetActivity()

        then:
        getAvailableMonthStringIndex.callCount == 1
        1 * controller.usageService.getActivity(PhoneOwnershipType.INDIVIDUAL) >> ["hi"]
        1 * controller.usageService.getActivity(PhoneOwnershipType.GROUP) >> ["hi"]
        response.json.staffData != null
        response.json.teamData != null
        response.json.currentMonthIndex != null

        when: "orgId query param"
        response.reset()
        params.clear()

        params.orgId = orgId
        controller.ajaxGetActivity()

        then:
        getAvailableMonthStringIndex.callCount == 2
        1 * controller.usageService.getActivityForOrg(PhoneOwnershipType.INDIVIDUAL, orgId) >> ["hi"]
        1 * controller.usageService.getActivityForOrg(PhoneOwnershipType.GROUP, orgId) >> ["hi"]
        response.json.staffData != null
        response.json.teamData != null
        response.json.currentMonthIndex != null

        when: "phone number query param"
        response.reset()
        params.clear()

        params.number = number
        controller.ajaxGetActivity()

        then:
        getAvailableMonthStringIndex.callCount == 3
        1 * controller.usageService.getActivityForNumber(number) >> ["hi"]
        response.json.numberData != null
        response.json.currentMonthIndex != null
    }

    @DirtiesRuntime
    void "test show endpoint"() {
        given:
        controller.usageService = Mock(UsageService)
        Long orgId = TestUtils.randIntegerUpTo(88) + 1
        Organization mockOrg = Mock()
        Organization.metaClass."static".get = { Long id -> mockOrg }
        MockedMethod getAvailableMonthStrings = MockedMethod.create(UsageUtils, "getAvailableMonthStrings") { 8 }

        when: "missing orgId"
        controller.show()

        then:
        0 * controller.usageService._
        response.redirectedUrl == "/usage/index"

        when: "has orgId"
        response.reset()
        params.clear()

        params.id = orgId
        Map model = controller.show()

        then:
        getAvailableMonthStrings.callCount > 0
        1 * controller.usageService.getStaffPhoneActivity(_ as DateTime, orgId) >> []
        1 * controller.usageService.getTeamPhoneActivity(_ as DateTime, orgId) >> []
        !response.redirectedUrl
        model.monthString != null
        model.availableMonthStrings != null
        model.currentTime != null
        model.staffUsageAndCosts != null
        model.teamUsageAndCosts != null
        model.staffPhoneCounts != null
        model.teamPhoneCounts != null
        model.org != null
        model.staffs != null
        model.teams != null
    }

    @DirtiesRuntime
    void "test index endpoint"() {
        given:
        controller.usageService = Mock(UsageService)
        MockedMethod getAvailableMonthStrings = MockedMethod.create(UsageUtils, "getAvailableMonthStrings") { 8 }

        when:
        Map model = controller.index()

        then:
        getAvailableMonthStrings.callCount > 0
        1 * controller.usageService
            .getOverallPhoneActivity(_ as DateTime, PhoneOwnershipType.INDIVIDUAL) >> []
        1 * controller.usageService
            .getOverallPhoneActivity(_ as DateTime, PhoneOwnershipType.GROUP) >> []
        !response.redirectedUrl
        model.monthString != null
        model.availableMonthStrings != null
        model.currentTime != null
        model.staffUsageAndCosts != null
        model.teamUsageAndCosts != null
        model.staffPhoneCounts != null
        model.teamPhoneCounts != null
        model.staffOrgs != null
        model.teamOrgs != null
    }
}
