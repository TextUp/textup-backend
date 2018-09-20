package org.textup.rest

import grails.compiler.GrailsTypeChecked
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsTypeChecked
@Secured("permitAll")
class PublicRecordController extends BaseController {

    static String namespace = "v1"

    //grailsApplication from superclass
    CallbackService callbackService
    CallbackStatusService callbackStatusService

    @Override
    protected String getNamespaceAsString() { namespace }

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }

    def save() {
        Result<Void> res = callbackService.validate(request, params)
        if (!res.success) {
            respondWithResult(Void, res)
        }
        else if (params.handle == Constants.CALLBACK_STATUS) {
            respondWithResult(Closure, callbackStatusService.process(params))
        }
        else {
            respondWithResult(Closure, callbackService.process(params))
        }
    }
}
