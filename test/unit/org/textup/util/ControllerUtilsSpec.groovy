package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class ControllerUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test try getting phone id"() {
        given:
        Long tId = TestUtils.randIntegerUpTo(88)
        Long sId = TestUtils.randIntegerUpTo(88)
        PhoneCache mockPhoneCache = Mock()
        Result retVal = Result.void()
        MockedMethod isAllowed = TestUtils.mock(Teams, "isAllowed") { Result.createSuccess(tId) }
        MockedMethod tryGetAuthId = TestUtils.mock(AuthUtils, "tryGetAuthId") { Result.createSuccess(sId) }
        MockedMethod getPhoneCache = TestUtils.mock(IOCUtils, "getPhoneCache") { mockPhoneCache }

        when: "for team"
        Result res = ControllerUtils.tryGetPhoneId(tId)

        then:
        1 * mockPhoneCache.mustFindAnyPhoneIdForOwner(tId, PhoneOwnershipType.GROUP) >> retVal
        isAllowed.callCount == 1
        isAllowed.callArguments[0] == [tId]
        tryGetAuthId.callCount == 0
        res == retVal

        when: "for staff"
        res = ControllerUtils.tryGetPhoneId()

        then:
        1 * mockPhoneCache.mustFindAnyPhoneIdForOwner(sId, PhoneOwnershipType.INDIVIDUAL) >> retVal
        isAllowed.callCount == 1
        tryGetAuthId.callCount == 1
        res == retVal

        cleanup:
        isAllowed.restore()
        tryGetAuthId.restore()
        getPhoneCache.restore()
    }

    void "test building errors"() {
        given:
        String errMsg1 = TestUtils.randString()
        String errMsg2 = TestUtils.randString()
        Result failRes = Result.createError([errMsg1, errMsg2], ResultStatus.BAD_REQUEST)

        when:
        Map map = ControllerUtils.buildErrors(null)

        then:
        map.errors == null

        when:
        map = ControllerUtils.buildErrors(failRes)

        then:
        map.errors instanceof Collection
        errMsg1 in map.errors*.message
        errMsg2 in map.errors*.message
        map.errors.every { it.statusCode == failRes.status.intStatus }
    }

    void "test normalizing pagination"() {
        given:
        int max = 888
        int offset = 12
        int total = 2000

        when: "no inputs"
        Map info = ControllerUtils.buildPagination(null, null)

        then:
        info.offset == 0
        info.max == ControllerUtils.DEFAULT_PAGINATION_MAX
        info.total == 0

        when: "no max"
        info = ControllerUtils.buildPagination(max: max, offset: offset)

        then:
        info.offset == offset
        info.max == max
        info.total == max

        when: "negative inputs"
        info = ControllerUtils.buildPagination(max: -8, offset: -8)

        then:
        info.offset == 0
        info.max == 0
        info.total == 0

        when: "max is too high"
        info = ControllerUtils.buildPagination(max: ControllerUtils.MAX_PAGINATION_MAX * 2, offset: offset)

        then:
        info.offset == offset
        info.max == ControllerUtils.MAX_PAGINATION_MAX
        info.total == ControllerUtils.MAX_PAGINATION_MAX

        when:
        info = ControllerUtils.buildPagination([max: max], total)

        then:
        info.offset == 0
        info.max == max
        info.total == total

        when:
        info = ControllerUtils.buildPagination([max: max, offset: offset], total)

        then:
        info.offset == offset
        info.max == max
        info.total == total
    }

    void "test building links"() {
        given:
        String key1 = TestUtils.randString()
        String val1 = TestUtils.randString()
        Map linkParams = [(key1): val1]

        when:
        Map info = ControllerUtils.buildLinks(null, null, null, null)

        then:
        info == [:]

        when:
        info = ControllerUtils.buildLinks(linkParams, null, null, null)

        then:
        info == [:]

        when:
        int offset1 = 10
        int max1 = 10
        int total1 = 20
        info = ControllerUtils.buildLinks(linkParams, offset1, max1, total1)

        then: "all inputs passed in as params to the link generator"
        info.next == null
        info.prev instanceof String
        info.prev.split("params")[1].contains(key1)
        info.prev.split("params")[1].contains(val1)
        info.prev.split("params")[1].contains("max")
        info.prev.split("params")[1].contains("offset")

        when:
        int offset2 = 0
        int max2 = 10
        int total2 = 100
        info = ControllerUtils.buildLinks(linkParams, offset2, max2, total2)

        then:
        info.prev == null
        info.next instanceof String
        info.next.split("params")[1].contains(key1)
        info.next.split("params")[1].contains(val1)
        info.next.split("params")[1].contains("max")
        info.next.split("params")[1].contains("offset")

        when:
        int offset3 = 40
        int max3 = 10
        int total3 = 100
        info = ControllerUtils.buildLinks(linkParams, offset3, max3, total3)

        then:
        info.prev instanceof String
        info.next instanceof String
    }
}
