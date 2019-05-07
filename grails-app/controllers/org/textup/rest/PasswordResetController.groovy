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
@Secured("permitAll")
@Transactional
class PasswordResetController extends BaseController {

	PasswordResetService passwordResetService

    @Override
    def save() {
        RequestUtils.tryGetJsonBody(request)
            .then { TypeMap body -> passwordResetService.start(body.string("username")) }
            .alwaysEnd { Result<?> res -> respondWithResult(res) }
    }

    @Override
    def update() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.tryGetJsonBody(request)
            .then { TypeMap body ->
                passwordResetService.finish(qParams.string("id"), body.string("password"))
            }
            .alwaysEnd { Result<?> res -> respondWithResult(res) }
    }
}
