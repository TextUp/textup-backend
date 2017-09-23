package org.textup.rest

import grails.compiler.GrailsCompileStatic
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.validator.Notification

@GrailsCompileStatic
@RestApi(name="Notification", description="Claim notifications")
@Secured("permitAll")
class NotifyController extends BaseController {

	static String namespace = "v1"

	TokenService tokenService

    @Override
    protected String getNamespaceAsString() { namespace }

    // Showing contents of token
    // -------------------------

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
        respondWithResult(Notification, tokenService.showNotification(token))
    }

    def index() { notAllowed() }
    def save() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }
}
