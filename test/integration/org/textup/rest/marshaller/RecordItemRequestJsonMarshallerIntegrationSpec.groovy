package org.textup.rest.marshaller

import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class RecordItemRequestJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        RecordCall rCall1 = TestUtils.buildRecordCall(ipr1.record)

        RecordItemRequest iReq1 = RecordItemRequest.tryCreate(p1, null, false).payload

        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createError([], ResultStatus.FORBIDDEN)
        }

        when:
        RequestUtils.trySet(RequestUtils.PAGINATION_OPTIONS, "not a string")
        Map json = TestUtils.objToJsonMap(iReq1)

        then:
        json.maxAllowedNumItems == ControllerUtils.MAX_PAGINATION_MAX
        json.phoneName == p1.buildName()
        json.phoneNumber == p1.number.prettyPhoneNumber
        json.totalNumItems > 0
        json.totalNumItems == iReq1.criteria.count()
        json.sections.size() == 1
        json.sections[0].recordItems.size() > 0
        json.sections[0].recordItems.size() == iReq1.criteria.count()

        when:
        RequestUtils.trySet(RequestUtils.PAGINATION_OPTIONS, [offset: 1000])
        json = TestUtils.objToJsonMap(iReq1)

        then:
        json.maxAllowedNumItems == ControllerUtils.MAX_PAGINATION_MAX
        json.phoneName == p1.buildName()
        json.phoneNumber == p1.number.prettyPhoneNumber
        json.totalNumItems > 0
        json.totalNumItems == iReq1.criteria.count()
        json.sections.size() == 1
        json.sections[0].recordItems.isEmpty()

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test marshalling exported by name"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone(s1)
        RecordItemRequest iReq1 = RecordItemRequest.tryCreate(p1, null, false).payload

        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createError([], ResultStatus.FORBIDDEN)
        }

        when:
        Map json = TestUtils.objToJsonMap(iReq1)

        then:
        json.exportedBy == null

        when:
        tryGetActiveAuthUser = MockedMethod.create(tryGetActiveAuthUser) { Result.createSuccess(s1) }
        json = TestUtils.objToJsonMap(iReq1)

        then:
        json.exportedBy == s1.name

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test marshalling specified timezone"() {
        given:
        String startString = TestUtils.randString()
        String endString = TestUtils.randString()
        String tzId = TestUtils.randString()

        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        RecordCall rCall1 = TestUtils.buildRecordCall(ipr1.record)

        RecordItemRequest iReq1 = RecordItemRequest.tryCreate(p1, null, false).payload
        iReq1.with {
            start = DateTime.now().minusDays(8)
            end = DateTime.now().minusDays(1)
        }

        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createError([], ResultStatus.FORBIDDEN)
        }
        MockedMethod buildFormattedStart = MockedMethod.create(iReq1, "buildFormattedStart") {
            startString
        }
        MockedMethod buildFormattedEnd = MockedMethod.create(iReq1, "buildFormattedEnd") {
            endString
        }

        when:
        Map json = TestUtils.objToJsonMap(iReq1)

        then:
        json.startDate == startString
        json.endDate == endString
        buildFormattedStart.latestArgs == [null]
        buildFormattedEnd.latestArgs == [null]

        when:
        RequestUtils.trySet(RequestUtils.TIMEZONE, tzId)
        json = TestUtils.objToJsonMap(iReq1)

        then:
        json.startDate == startString
        json.endDate == endString
        buildFormattedStart.latestArgs == [tzId]
        buildFormattedEnd.latestArgs == [tzId]

        cleanup:
        tryGetActiveAuthUser?.restore()
        buildFormattedStart?.restore()
        buildFormattedEnd?.restore()
    }
}
