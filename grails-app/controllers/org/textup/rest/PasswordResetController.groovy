package org.textup.rest

import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@RestApi(name="PasswordReset", description = "Password reset")
@Secured("permitAll")
class PasswordResetController extends BaseController {

	static allowedMethods = [index:"GET", requestReset:"POST",
        resetPassword:"PUT", delete:"DELETE"]

	PasswordResetService passwordResetService

    def index() { notAllowed() }
    def delete() { notAllowed() }

    @RestApiMethod(description="Request a password reset")
    @RestApiResponseObject(objectIdentifier = "[passwordResetRequest]")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request or \
            could not send email."),
        @RestApiError(code="404", description="Staff with passed-in username \
            not found or has no email."),
    ])
    def requestReset() {
        if (!validateJsonRequest(request)) { return }
        Map info = request.properties.JSON as Map
        if (!info.username) {
            return badRequest()
        }
        Result res = passwordResetService.requestReset(info.username as String)
        if (res.success) {
            ok()
        }
        else { handleResultFailure(res) }
    }

    @RestApiMethod(description="Reset password with a valid reset tokentoken")
    @RestApiResponseObject(objectIdentifier = "[newPasswordRequest]")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request or expired token."),
        @RestApiError(code="404", description="Could not find token"),
        @RestApiError(code="422", description="The new password is invalid.")
    ])
    def resetPassword() {
        if (!validateJsonRequest(request)) { return }
        Map info = request.properties.JSON as Map
        if (!info.token || !info.password) {
            return badRequest()
        }
        Result res = passwordResetService.resetPassword(info.token as String,
            info.password as String)
        if (res.success) {
            ok()
        }
        else { handleResultFailure(res) }
    }
}
