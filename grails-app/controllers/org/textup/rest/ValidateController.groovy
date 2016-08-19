package org.textup.rest
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@RestApi(name="Validate", description = "Validates login credentials and lock codes")
@Secured("permitAll")
class ValidateController extends BaseController {

	AuthService authService

	@RestApiMethod(description='''Validate login credentails or lockCode.
		If validating login credentials, must pass in "username" and "password".
		if validating lockCode, must pass in "username" and "lockCode"''')
    @RestApiErrors(apierrors=[
    	@RestApiError(code="204", description="Passed-in fields are valid."),
        @RestApiError(code="400", description="Missing required fields."),
        @RestApiError(code="403", description="Passed-in fields are invalid.")
    ])
	def save() {
		Map data = request.properties.JSON as Map
		String un = data.username
		if (un && data.password) {
			authService.isValidUsernamePassword(un, data.password as String) ?
				noContent() : forbidden()
		}
		else if (un && data.lockCode) {
			authService.isValidLockCode(un, data.lockCode as String) ?
				noContent() : forbidden()
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
