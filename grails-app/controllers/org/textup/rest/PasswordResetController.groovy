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
@Secured(Roles.PUBLIC)
@Transactional
class PasswordResetController extends BaseController {

	PasswordResetService passwordResetService

    @Override
    void save() {
        RequestUtils.tryGetJsonBody(request)
            .then { TypeMap body -> passwordResetService.start(body.string("username")) }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }

    @Override
    void update() {
        RequestUtils.tryGetJsonBody(request)
            .then { TypeMap body ->
                passwordResetService.finish(body.string("token"), body.string("password"))
            }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }
}
