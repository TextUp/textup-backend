package org.textup.rest

import grails.converters.JSON
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*

@Secured("permitAll")
class PublicRecordController extends BaseController {

    static namespace = "v1"

    //grailsApplication from superclass
    //authService from superclass
    def recordService
    def callbackService

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }

    def save() {
        callbackService.validate(request, params).then({ ->
            if (params.handle == Constants.CALLBACK_STATUS) {
                String apiId = params.CallSid ?: params.MessageSid
                ReceiptStatus status = params.CallStatus ?
                    ReceiptStatus.translate(params.CallStatus) :
                    ReceiptStatus.translate(params.MessageStatus)
                Integer duration = Helpers.toInteger(params.CallDuration)
                //update status
                Result<Closure> res = recordService.updateStatus(status, apiId, duration)
                if (!res.success && params.ParentCallSid) {
                    res = recordService.updateStatus(params.ParentCallSid, apiId, duration)
                }
                res
            }
            else { callbackService.process(params) }
        })
    }
}
