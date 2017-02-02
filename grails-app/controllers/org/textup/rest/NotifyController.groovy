package org.textup.rest

import grails.compiler.GrailsCompileStatic
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.validator.Notification
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@RestApi(name="Notification", description="Claim notifications")
@Secured("permitAll")
class NotifyController extends BaseController {

	static namespace = "v1"

	TokenService tokenService

    def index() { notAllowed() }

    @RestApiMethod(description="Show contents of the notification")
    @RestApiResponseObject(objectIdentifier = "[notification]")
    @RestApiParams(params=[
        @RestApiParam(name="token", type="String",
        	paramType=RestApiParamType.PATH, description="Value of the notification token")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Token has expired."),
        @RestApiError(code="404", description="Token could not be found."),
    ])
    def show() {
    	String token = params.id
    	Result<Notification> res = tokenService.showNotification(token)
    	if (res.success) {
			respond(res.payload, [status:OK])
    	}
    	else { handleResultFailure(res) }
    }

    def save() { notAllowed() }

    def update() { notAllowed() }

    def delete() { notAllowed() }
}
