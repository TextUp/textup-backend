package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class SessionJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        IncomingSession is1 = TestUtils.buildSession(p1)

    	when:
    	Map json = TestUtils.objToJsonMap(is1)

    	then:
    	json.id == is1.id
        json.isSubscribedToText == is1.isSubscribedToText
        json.isSubscribedToCall == is1.isSubscribedToCall
        json.number == is1.number.prettyPhoneNumber
        json.whenCreated == is1.whenCreated.toString()
        json.lastSentInstructions == is1.lastSentInstructions.toString()
        json.shouldSendInstructions == is1.shouldSendInstructions
        json.staff == is1.phone.owner.ownerId
        json.team == null
    }
}
