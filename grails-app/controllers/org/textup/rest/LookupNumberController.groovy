package org.textup.rest

import grails.converters.JSON
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import static org.springframework.http.HttpStatus.*
import org.textup.*
import grails.transaction.Transactional
import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.TwilioRestException
import com.twilio.sdk.resource.list.AvailablePhoneNumberList
import com.twilio.sdk.resource.list.IncomingPhoneNumberList

@Secured(["ROLE_ADMIN", "ROLE_USER"])
class LookupNumberController extends BaseController {

    static namespace = "v1"
    //grailsApplication from superclass
    //authService from superclass

    def index() {
        def twilioConfig = grailsApplication.config.textup.apiKeys.twilio
        Staff s1 = authService.loggedInAndActive
        if (s1) {
            Location loc = s1.org.location
            try {
                TwilioRestClient client = new TwilioRestClient(twilioConfig.sid, twilioConfig.authToken)
                Map<String, String> existingParams = ["FriendlyName":twilioConfig.available]
                IncomingPhoneNumberList existingNumbers = client.getAccount().getIncomingPhoneNumbers(existingParams);
                Map<String, String> newParams = [
                    "NearLatLong":"${loc.lat}, ${loc.lon}".toString(),
                    "ExcludeAllAddressRequired":"false",
                    "ExcludeLocalAddressRequired":"false",
                    "ExcludeForeignAddressRequired":"false"
                ]
                AvailablePhoneNumberList newNumbers = client.getAccount().getAvailablePhoneNumbers(newParams, "US", "Local");

                List availableNumbers = []
                existingNumbers.toList().each {
                    availableNumbers << [phoneNumber:it.phoneNumber, sid:it.sid]
                }
                newNumbers.toList().each {
                    availableNumbers << [friendlyName:it.friendlyName, region:it.region,
                        isoCountry:it.isoCountry, phoneNumber:it.phoneNumber]
                }
                withFormat {
                    json {
                        respond availableNumbers, [status:OK]
                    }
                }
            }
            catch (TwilioRestException e) {
                log.error("LookupNumberController.index: ${e.message}")
                error()
            }
        }
        else { forbidden() }
    }

    def show() { notAllowed() }
    def save() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }
}
