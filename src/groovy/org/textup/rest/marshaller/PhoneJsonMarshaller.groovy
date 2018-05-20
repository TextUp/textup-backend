package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.grails.web.util.WebUtils
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*



import org.hibernate.Session



@GrailsTypeChecked
class PhoneJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Phone p1 ->

        Map json = [:]
        json.with {
            id = p1.id
            number = p1.number.e164PhoneNumber
            awayMessage = p1.awayMessage
            voice = p1.voice.toString()
            mandatoryEmergencyMessage = Constants.AWAY_EMERGENCY_MESSAGE
            tags = p1.getTags() ?: []
            language = p1.language.toString()
        }

        // if the logged-in user is an owner of this phone, show integrated availability information

        Phone.withSession { Session sess1 ->
            Staff loggedIn = authService.getLoggedInAndActive()
            // for rare cases during testing when phone is no longer attached,
            // re-fetch the phone. Using `.attach()` may throw a DuplicateKeyException
            // so we just re-fetch to avoid this
            if (!p1.isAttached()) {
                p1 = Phone.get(p1.id)
            }

            if (p1.owner.all.contains(loggedIn)) {
                NotificationPolicy np1 = p1.owner.getPolicyForStaff(loggedIn.id)
                if (np1) {
                    json.with {
                        useStaffAvailability = np1.useStaffAvailability
                        manualSchedule = np1.manualSchedule
                        isAvailable = np1.isAvailable
                        isAvailableNow = np1.isAvailableNow()
                        schedule = np1.schedule
                    }
                }
                else { // default to staff availability
                    json.useStaffAvailability = true
                    json.isAvailableNow = loggedIn.isAvailableNow()
                }
            }
        }

        json
    }

    PhoneJsonMarshaller() {
        super(Phone, marshalClosure)
    }
}
