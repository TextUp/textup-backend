package org.textup.rest

import grails.compiler.GrailsTypeChecked
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsTypeChecked
@RestApi(name="Validate", description = "Validates login credentials and lock codes")
@Secured("permitAll")
class ValidateController extends BaseController {

    // this utility endpoint is NOT namespaced, namespace is therefore NULL not empty string
	static String namespace = null

	AuthService authService

	@Override
    protected String getNamespaceAsString() { namespace }

	@RestApiMethod(description='''Validate login credentails or lockCode.
		If validating login credentials, must pass in "username" and "password".
		if validating lockCode, must pass in "username" and "lockCode"''')
    @RestApiErrors(apierrors=[
    	@RestApiError(code="204", description="Passed-in fields are valid."),
        @RestApiError(code="400", description="Missing required fields."),
        @RestApiError(code="403", description="Passed-in fields are invalid.")
    ])
	def save() {
		Map data = getJsonPayload(request)
        if (data == null) { return }
		String un = data.username
		if (un && data.password) {
            AuthUtils.isValidCredentials(un, data.password as String) ? noContent() : forbidden()
		}
		else if (un && data.lockCode) {
            // TODO
            Result<Staff> res = AuthUtils.tryGetAuthUser()
            if (AuthUtils.isSecureStringValid(res.payload?.lockCode, data.lockCode as String)) {
                noContent()
            }
            else { forbidden() }
		}
		else { badRequest() }
	}

	// Not allowed
	// -----------

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }
}
