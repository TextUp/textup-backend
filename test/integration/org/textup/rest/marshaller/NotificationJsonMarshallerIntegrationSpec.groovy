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

    	when:
        RequestUtils.trySet(RequestUtils.STAFF_ID, "not a number")
    	Map json = TestUtils.objToJsonMap(notif1)

    	then:
        json.id == s1.id
        json.name == s1.name
        json.phoneNumber.noFormatNumber == p1.number.number
        json.type == PhoneOwnershipType.INDIVIDUAL.toString()
        json.details instanceof Collection
        json.details.size() > 0
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
        json.id == s1.id
        json.name == s1.name
        json.phoneNumber.noFormatNumber == p1.number.number
        json.type == PhoneOwnershipType.INDIVIDUAL.toString()
        json.details instanceof Collection
        json.details.size() > 0
        json.numVoicemail != null
        json.numIncomingText != null
        json.numIncomingCall != null
        json.incomingNames != null
        json.numOutgoingText != null
        json.numOutgoingCall != null
        json.outgoingNames != null
    }
}
