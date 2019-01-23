package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
@Transactional
class NumberController extends BaseController {

    NumberService numberService

    // requesting list of available twilio numbers
    @Override
    void index() {
        AuthUtils.tryGetAuthUser()
            .then { Staff authUser -> numberService.listExistingNumbers().curry(authUser) }
            .then { Staff authUser, Collection<AvailablePhoneNumber> iNums ->
                numberService.listNewNumbers(params.string("search"), authUser.org.location)
                    .curry(iNums)
            }
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Collection<AvailablePhoneNumber> iNums, Collection<AvailablePhoneNumber> lNums ->
                Collection<AvailablePhoneNumber> allNums = iNums + lNums
                respondWithMany({ allNums.size() }, { allNums }, params, MarshallerUtils.KEY_NUMBER)
            }
    }

    // validating phone number against the twilio phone number validator
    @Override
    void show() {
        PhoneNumber.tryCreate(params.string("id"))
            .then { PhoneNumber pNum -> numberService.validateNumber(pNum) }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }

    // requesting and checking phone number validation tokens
    @Override
    void save() {
        RequestUtils.tryGetJsonBody(request)
            .then { TypeMap body -> PhoneNumber.tryCreate(body.string("phoneNumber")).curry(body) }
            .then { TypeMap body, PhoneNumber pNum ->
                String token = body.string("token")
                token ?
                    numberService.finishVerifyOwnership(token, pNum) :
                    numberService.startVerifyOwnership(pNum)
            }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }
}
