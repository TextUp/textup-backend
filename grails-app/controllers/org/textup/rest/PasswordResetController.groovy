package org.textup.rest

import grails.compiler.GrailsTypeChecked
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsTypeChecked
@Secured(Roles.PUBLIC)
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
