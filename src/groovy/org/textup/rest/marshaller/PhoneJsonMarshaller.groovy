package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.type.*

@GrailsTypeChecked
class PhoneJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        Phone p1 ->

        Map json = [:]
        json.with {
            id = p1.id
            number = p1.number.e164PhoneNumber
            tags = p1.getTags() ?: []
            language = p1.language.toString()

            awayMessage = p1.awayMessage
            mandatoryEmergencyMessage = Constants.AWAY_EMERGENCY_MESSAGE
            useVoicemailRecordingIfPresent = p1.useVoicemailRecordingIfPresent
            voice = p1.voice.toString()
            voicemailRecording = p1.media?.getMostRecentByType(MediaType.AUDIO_TYPES)
        }

        AuthService authService = grailsApplication.mainContext.getBean(AuthService)
        Staff loggedIn = authService.getLoggedInAndActive()
        // for rare cases during testing when phone is no longer attached,
        // re-fetch the phone. Using `.attach()` may throw a DuplicateKeyException
        // so we just re-fetch to avoid this
        if (!p1.isAttached()) {
            p1 = Phone.get(p1.id)
        }
        // if the logged-in user is an owner of this phone, show integrated availability information
        List<Staff> allStaff = p1.owner.all
        if (allStaff.contains(loggedIn)) {
            json.availability = new StaffPolicyAvailability(p1, loggedIn, false)
            json.others = allStaff
                .findAll { Staff s1 -> s1 != loggedIn }
                .collect { Staff s1 -> new StaffPolicyAvailability(p1, s1, true) }
        }

        json
    }

    PhoneJsonMarshaller() {
        super(Phone, marshalClosure)
    }
}
