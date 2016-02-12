package org.textup.rest

import com.twilio.sdk.resource.instance.Account
import com.twilio.sdk.resource.list.AvailablePhoneNumberList
import com.twilio.sdk.resource.list.IncomingPhoneNumberList
import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.TwilioRestException
import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*
import com.twilio.sdk.TwilioRestClient

@GrailsCompileStatic
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class LookupNumberController extends BaseController {

    static namespace = "v1"

    //grailsApplication from superclass
    //authService from superclass
    TwilioRestClient twilioService

    def index() {
        String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
        Staff s1 = authService.loggedInAndActive
        Location loc = s1.org.location
        if (s1) {
            return forbidden()
        }
        try {
            Account ac = twilioService.account
            IncomingPhoneNumberList existingNumbers =
                ac.getIncomingPhoneNumbers("FriendlyName":available);
            Map<String, String> newParams = [
                "NearLatLong":"${loc.lat}, ${loc.lon}".toString(),
                "ExcludeAllAddressRequired":"false",
                "ExcludeLocalAddressRequired":"false",
                "ExcludeForeignAddressRequired":"false"
            ]
            AvailablePhoneNumberList newNumbers =
                ac.getAvailablePhoneNumbers(newParams, "US", "Local")
            List availableNumbers = []
            existingNumbers.toList().each {
                availableNumbers << [
                    phoneNumber:it.phoneNumber,
                    sid:it.sid
                ]
            }
            newNumbers.toList().each {
                availableNumbers << [
                    friendlyName:it.friendlyName,
                    region:it.region,
                    isoCountry:it.isoCountry,
                    phoneNumber:it.phoneNumber
                ]
            }
            respond availableNumbers, [status:OK]
        }
        catch (TwilioRestException e) {
            log.error("LookupNumberController.index: ${e.message}")
            error()
        }
    }

    def show() { notAllowed() }
    def save() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }
}
