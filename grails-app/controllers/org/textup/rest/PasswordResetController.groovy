package org.textup.rest

import grails.compiler.GrailsCompileStatic
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsCompileStatic
@RestApi(name="PasswordReset", description = "Password reset")
@Secured("permitAll")
class PasswordResetController extends BaseController {

    // this utility endpoint is NOT namespaced, namespace is therefore NULL not empty string
    static String namespace = null
	static allowedMethods = [index:"GET", requestReset:"POST", resetPassword:"PUT", delete:"DELETE"]

	PasswordResetService passwordResetService

    @Override
    protected String getNamespaceAsString() { namespace }

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
        Map info = getJsonPayload(request)
        if (info == null) { return }
        if (!info.username) {
            badRequest()
        }
        else { respondWithResult(Void, passwordResetService.start(info.username as String)) }
    }

    @RestApiMethod(description="Reset password with a valid reset tokentoken")
    @RestApiResponseObject(objectIdentifier = "[newPasswordRequest]")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request or expired token."),
        @RestApiError(code="404", description="Could not find token"),
        @RestApiError(code="422", description="The new password is invalid.")
    ])
    def resetPassword() {
        Map info = getJsonPayload(request)
        if (info == null) { return }
        if (!info.token || !info.password) {
            return badRequest()
        }
        Result<Staff> res = passwordResetService.finish(info.token as String, info.password as String)
        if (res.success) {
            noContent()
        }
        else { respondWithResult(Staff, res) }
    }
}
