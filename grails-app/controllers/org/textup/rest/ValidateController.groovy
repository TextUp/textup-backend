package org.textup.rest

import grails.compiler.GrailsTypeChecked
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class ValidateController extends BaseController {

    @Override
	void save() {
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body -> AuthUtils.tryGetAuthUser().curry(body) }
            .ifFail { Result<?> failRes -> respondWithResult(CLASS, failRes) }
            .thenEnd { TypeMap body, Staff authUser ->
                boolean isValid = data.password ?
                    AuthUtils.isValidCredentials(authUser.username, data.string("password")) :
                    AuthUtils.isSecureStringValid(authUser.lockCode, data.string("lockCode"))
                isValid ? noContent() : forbidden()
            }
	}
}
