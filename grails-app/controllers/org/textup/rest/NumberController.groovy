package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.validator.PhoneNumber
import org.textup.validator.AvailablePhoneNumber

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class NumberController extends BaseController {

    NumberService numberService

    // requesting list of available twilio numbers
    @Transactional(readOnly=true)
    @Override
    void index() {
        AuthUtils.tryGetAuthUser()
            .then { Staff authUser -> numberService.listExistingNumbers().curry(authUser) }
            .then { Staff authUser, Collection<AvailablePhoneNumber> iNums ->
                numberService.listNewNumbers(params.string("search"), authUser.org.location)
                    .curry(iNums)
            }
            .ifFail { Result<?> failRes -> respondWithResult(null, failRes) }
            .thenEnd { Collection<AvailablePhoneNumber> iNums, Collection<AvailablePhoneNumber> lNums ->
                Collection<AvailablePhoneNumber> availableNums = iNums + lNums
                respondWithMany(AvailablePhoneNumber, { availableNums.size() }, { availableNums })
            }
    }

    // validating phone number against the twilio phone number validator
    @Transactional(readOnly=true)
    @Override
    void show() {
        PhoneNumber.tryCreate(params.string("id"))
            .then { PhoneNumber pNum -> numberService.validateNumber(pNum) }
            .anyEnd { Result<?> res -> respondWithResult(AvailablePhoneNumber, res) }
    }

    // requesting and checking phone number validation tokens
    @Override
    void save() {
        tryGetJsonPayload(null, request)
            .then { TypeMap body -> PhoneNumber.tryCreate(body.string("phoneNumber")).curry(body) }
            .then { TypeMap body, PhoneNumber pNum ->
                String token = body.string("token")
                token ?
                    numberService.finishVerifyOwnership(token, pNum) :
                    numberService.startVerifyOwnership(pNum)
            }
            .anyEnd { Result<?> res -> respondWithResult(null, res) }
    }
}
