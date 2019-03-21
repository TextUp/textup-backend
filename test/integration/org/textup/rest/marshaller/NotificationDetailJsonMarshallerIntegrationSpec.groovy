package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class NotificationDetailJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling details for contacts vs tags"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
        NotificationDetail nd2 = NotificationDetail.tryCreate(gpr1.toWrapper()).payload

        when:
        Map json = TestUtils.objToJsonMap(nd1)

        then:
        json.items instanceof Collection
        json.items.isEmpty()
        json.id == ipr1.id
        json.name == ipr1.name
        json.isTag == false

        when:
        json = TestUtils.objToJsonMap(nd2)

        then:
        json.items instanceof Collection
        json.items.isEmpty()
        json.id == gpr1.id
        json.name == gpr1.name
        json.isTag
    }

    void "test marshalling with staff id on request"() {
        given:
        Long invalidStaffId = -88

        Staff s1 = TestUtils.buildStaff()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        RecordItem rItem1 = TestUtils.buildRecordItem()
        RecordItem rItem2 = TestUtils.buildRecordItem()
        NotificationDetail nd1 = NotificationDetail.tryCreate(spr1.toWrapper()).payload

        nd1.items << rItem1
        MockedMethod buildAllowedItemsForOwnerPolicy = MockedMethod.create(nd1, "buildAllowedItemsForOwnerPolicy") {
            [rItem2]
        }

        ReadOnlyOwnerPolicy rop1 = GroovyMock()
        MockedMethod findReadOnlyOrDefaultForOwnerAndStaff = MockedMethod.create(OwnerPolicies, "findReadOnlyOrDefaultForOwnerAndStaff") {
            rop1
        }

        when: "no staff id on request"
        RequestUtils.trySet(RequestUtils.STAFF_ID, "not a number")

        Map json = TestUtils.objToJsonMap(nd1)

        then:
        findReadOnlyOrDefaultForOwnerAndStaff.notCalled
        buildAllowedItemsForOwnerPolicy.notCalled
        json.items.size() == 1
        json.items[0].id == rItem1.id

        when: "invalid staff id"
        RequestUtils.trySet(RequestUtils.STAFF_ID, invalidStaffId)

        json = TestUtils.objToJsonMap(nd1)

        then:
        findReadOnlyOrDefaultForOwnerAndStaff.notCalled
        buildAllowedItemsForOwnerPolicy.notCalled
        json.items.size() == 1
        json.items[0].id == rItem1.id

        when: "valid staff id"
        RequestUtils.trySet(RequestUtils.STAFF_ID, s1.id)

        json = TestUtils.objToJsonMap(nd1)

        then:
        findReadOnlyOrDefaultForOwnerAndStaff.latestArgs == [spr1.phone.owner, s1]
        buildAllowedItemsForOwnerPolicy.latestArgs == [rop1]
        json.items.size() == 1
        json.items[0].id == rItem2.id

        cleanup:
        buildAllowedItemsForOwnerPolicy?.restore()
        findReadOnlyOrDefaultForOwnerAndStaff?.restore()
    }
}
