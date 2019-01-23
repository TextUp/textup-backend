package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
@Transactional
class ValidateController extends BaseController {

    @Override
	void save() {
        RequestUtils.tryGetJsonBody(request)
            .then { TypeMap body -> AuthUtils.tryGetAuthUser().curry(body) }
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { TypeMap body, Staff authUser ->
                boolean isValid = data.password ?
                    AuthUtils.isValidCredentials(authUser.username, data.string("password")) :
                    AuthUtils.isSecureStringValid(authUser.lockCode, data.string("lockCode"))
                isValid ? noContent() : forbidden()
            }
	}
}
