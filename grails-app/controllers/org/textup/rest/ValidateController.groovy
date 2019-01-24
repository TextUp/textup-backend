package org.textup.rest

import grails.compiler.GrailsTypeChecked
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
class ValidateController extends BaseController {

    @Override
	void save() {
        RequestUtils.tryGetJsonBody(request)
            .then { TypeMap body -> AuthUtils.tryGetAuthUser().curry(body) }
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { TypeMap body, Staff authUser ->
                boolean isValid = body.password ?
                    AuthUtils.isValidCredentials(authUser.username, body.string("password")) :
                    AuthUtils.isSecureStringValid(authUser.lockCode, body.string("lockCode"))
                isValid ?
                    renderStatus(ResultStatus.NO_CONTENT) :
                    renderStatus(ResultStatus.FORBIDDEN)
            }
	}
}
