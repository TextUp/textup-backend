package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class SessionJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { IncomingSession is1 ->
        Map json = [:]
        json.with {
            id                     = is1.id
            isSubscribedToCall     = is1.isSubscribedToCall
            isSubscribedToText     = is1.isSubscribedToText
            lastSentInstructions   = is1.lastSentInstructions
            links                  = MarshallerUtils.buildLinks(RestUtils.RESOURCE_SESSION, is1.id)
            number                 = is1.number
            shouldSendInstructions = is1.shouldSendInstructions
            whenCreated            = is1.whenCreated

            if (is1.phone.owner.type == PhoneOwnershipType.INDIVIDUAL) {
                staff = is1.phone.owner.ownerId
            }
            else {
                team = is1.phone.owner.ownerId
            }
        }
        json
    }

    SessionJsonMarshaller() {
        super(IncomingSession, marshalClosure)
    }
}
