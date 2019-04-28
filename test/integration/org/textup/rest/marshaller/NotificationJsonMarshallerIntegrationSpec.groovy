package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class NotificationJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
    	given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone(s1)
        Notification notif1 = TestUtils.buildNotification(p1)
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy()

        NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
        MockedMethod buildDetailsWithAllowedItemsForOwnerPolicy = MockedMethod.create(notif1, "buildDetailsWithAllowedItemsForOwnerPolicy") {
            [nd1]
        }

        MockedMethod findReadOnlyOrDefaultForOwnerAndStaff = MockedMethod.create(OwnerPolicies, "findReadOnlyOrDefaultForOwnerAndStaff") {
            op1
        }

    	when:
        RequestUtils.trySet(RequestUtils.STAFF_ID, "not a number")
    	Map json = TestUtils.objToJsonMap(notif1)

    	then:
        findReadOnlyOrDefaultForOwnerAndStaff.notCalled
        buildDetailsWithAllowedItemsForOwnerPolicy.notCalled
        json.id == s1.id
        json.name == s1.name
        json.phoneNumber == p1.number.prettyPhoneNumber
        json.type == PhoneOwnershipType.INDIVIDUAL.toString()
        json.details instanceof Collection
        json.details.size() > 0
        json.details.any { it.id == ipr1.id } == false
        json.numVoicemail == null
        json.numIncomingText == null
        json.numIncomingCall == null
        json.incomingNames == null
        json.numOutgoingText == null
        json.numOutgoingCall == null
        json.outgoingNames == null

        when:
        RequestUtils.trySet(RequestUtils.STAFF_ID, s1.id)
        json = TestUtils.objToJsonMap(notif1)

        then:
        findReadOnlyOrDefaultForOwnerAndStaff.callCount == 2 // Notification and NotificationDetail marshallers
        // in NotificationJsonMarshaller
        findReadOnlyOrDefaultForOwnerAndStaff.argsForCount(1) == [p1.owner, s1]
        // in NotificationDetailJsonMarshaller
        findReadOnlyOrDefaultForOwnerAndStaff.argsForCount(2) == [ipr1.phone.owner, s1]
        buildDetailsWithAllowedItemsForOwnerPolicy.latestArgs == [op1]
        json.id == s1.id
        json.name == s1.name
        json.phoneNumber == p1.number.prettyPhoneNumber
        json.type == PhoneOwnershipType.INDIVIDUAL.toString()
        json.details instanceof Collection
        json.details.size() > 0
        json.details.any { it.id == ipr1.id }
        json.numVoicemail != null
        json.numIncomingText != null
        json.numIncomingCall != null
        json.incomingNames != null
        json.numOutgoingText != null
        json.numOutgoingCall != null
        json.outgoingNames != null

        cleanup:
        findReadOnlyOrDefaultForOwnerAndStaff?.restore()
        buildDetailsWithAllowedItemsForOwnerPolicy?.restore()
    }
}
