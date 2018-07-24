package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.PhoneOwnershipType

@GrailsCompileStatic
class SessionJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, IncomingSession sess ->

        Map json = [:]
        json.with {
            id = sess.id
            isSubscribedToText = sess.isSubscribedToText
            isSubscribedToCall = sess.isSubscribedToCall
            number = sess.number.e164PhoneNumber
            whenCreated = sess.whenCreated
            lastSentInstructions = sess.lastSentInstructions
            shouldSendInstructions = sess.shouldSendInstructions
        }
        if (sess.phone.owner.type == PhoneOwnershipType.INDIVIDUAL) {
            json.staff = sess.phone.owner.ownerId
        }
        else {
            json.team = sess.phone.owner.ownerId
        }

        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"session", action:"show", id:sess.id, absolute:false)]
        json
    }

    SessionJsonMarshaller() {
        super(IncomingSession, marshalClosure)
    }
}
