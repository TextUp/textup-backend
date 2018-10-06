package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.validator.PhoneNumber
import org.textup.validator.AvailablePhoneNumber

@GrailsCompileStatic
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class NumberController extends BaseController {

    static String namespace = "v1"

    @Override
    protected String getNamespaceAsString() { namespace }

    //grailsApplication from superclass
    AuthService authService
    NumberService numberService
    ResultFactory resultFactory

    // requesting list of available twilio numbers
    @Transactional(readOnly=true)
    def index() {
        String available = grailsApplication.flatConfig["textup.apiKeys.twilio.available"]
        Staff s1 = authService.loggedInAndActive
        if (!s1) {
            return forbidden()
        }
        Result<Collection<AvailablePhoneNumber>> res = numberService
            .listExistingNumbers()
            .then({ Collection<AvailablePhoneNumber> iNums ->
                numberService
                    .listNewNumbers(params.search as String, s1.org.location)
                    .then({ Collection<AvailablePhoneNumber> lNums -> resultFactory.success(iNums + lNums) })
            })
        if (res.success) {
            Collection<AvailablePhoneNumber> availableNums = res.payload
            respondWithMany(AvailablePhoneNumber, { availableNums.size() }, { availableNums })
        }
        else { respondWithResult(Object, res) }
    }

    // requesting and checking phone number validation tokens
    def save() {
        Map vInfo = getJsonPayload(request)
        if (vInfo == null) { return }
        PhoneNumber pNum = new PhoneNumber(number:vInfo.phoneNumber as String)
        String token = vInfo.token
        if (pNum.validate()) {
            Result<Void> res = token ?
                numberService.finishVerifyOwnership(token, pNum) :
                numberService.startVerifyOwnership(pNum)
            respondWithResult(Void, res)
        }
        else {
            respondWithResult(PhoneNumber, resultFactory.failWithValidationErrors(pNum.errors))
        }
    }

    // validating phone number against the twilio phone number validator
    @Transactional(readOnly=true)
    def show() {
        PhoneNumber pNum = new PhoneNumber(number:params.id as String)
        if (!pNum.validate()) {
            return respondWithResult(PhoneNumber, resultFactory.failWithValidationErrors(pNum.errors))
        }
        respondWithResult(AvailablePhoneNumber, numberService.validateNumber(pNum))
    }

    def update() { notAllowed() }
    def delete() { notAllowed() }
}
