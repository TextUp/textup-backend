package org.textup.rest

import com.twilio.exception.TwilioException
import com.twilio.rest.api.v2010.account.availablephonenumbercountry.Local
import com.twilio.rest.api.v2010.account.IncomingPhoneNumber
import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.validator.PhoneNumber
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class LookupNumberController extends BaseController {

    static namespace = "v1"

    //grailsApplication from superclass
    //authService from superclass
    TokenService tokenService

    def index() {
        String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
        Staff s1 = authService.loggedInAndActive
        Location loc = s1.org.location
        if (!s1) {
            return forbidden()
        }
        try {
            List availableNumbers = []
            // reuse any available numbers we already own
            IncomingPhoneNumber
                .reader()
                .setFriendlyName(available)
                .read()
                .each { IncomingPhoneNumber eNum ->
                    availableNumbers << [
                        phoneNumber:eNum.phoneNumber.endpoint,
                        sid:eNum.sid
                    ]
                }
            // also present some options for numbers we don't already own
            Local
                .reader("US")
                .setSmsEnabled(true)
                .setMmsEnabled(true)
                .setVoiceEnabled(true)
                .setNearLatLong("${loc.lat},${loc.lon}".toString())
                .read()
                .each { Local lNum ->
                    availableNumbers << [
                        phoneNumber:lNum.phoneNumber.endpoint,
                        region: "${lNum.region}, ${lNum.isoCountry}"
                    ]
                }
            respond availableNumbers, [status:OK]
        }
        catch (TwilioException e) {
            log.error("LookupNumberController.index: ${e.message}")
            error()
        }
    }

    def save() {
        Map vInfo = (request.properties.JSON as Map) as Map
        PhoneNumber pNum = new PhoneNumber(number:vInfo.phoneNumber as String)
        String token = vInfo.token
        if (!pNum.validate()) {
            return failsValidation()
        }
        Result res = token ? tokenService.verifyNumber(token, pNum) :
            tokenService.requestVerify(pNum)
        if (res.success) {
            noContent()
        }
        else { handleResultFailure(res) }
    }

    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }
}
