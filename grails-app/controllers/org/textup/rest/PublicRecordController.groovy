package org.textup.rest

import grails.compiler.GrailsTypeChecked
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.LogLevel
import org.textup.type.ReceiptStatus
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@Secured("permitAll")
class PublicRecordController extends BaseController {

    static String namespace = "v1"

    //grailsApplication from superclass
    AuthService authService
    RecordService recordService
    CallbackService callbackService
    TwimlBuilder twimlBuilder
    CallService callService

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
            // first wait for a few seconds to allow for the receipts to be saved
            TimeUnit.SECONDS.sleep(5)
            // then continue handling status
            handleUpdateStatus(params)
        }
        else {
            respondWithResult(Closure, callbackService.process(params))
        }
    }
    protected def handleUpdateStatus(GrailsParameterMap params) {
        String apiId = params.CallSid ?: params.MessageSid
        ReceiptStatus status = params.CallStatus ?
            ReceiptStatus.translate(params.CallStatus as String) :
            ReceiptStatus.translate(params.MessageStatus as String)
        Integer duration = Helpers.to(Integer, params.CallDuration)
        // update status
        Result<Closure> res = recordService.updateStatus(status, apiId, duration)
        if (!res.success && params.ParentCallSid) {
            apiId = params.ParentCallSid as String
            res = recordService.updateStatus(status, apiId, duration)
        }
        // If multiple phone numbers on a call and the status is
        // failure, then retry the call. See CallService.start for
        // the parameters passed into the status callback
        if (params.CallSid && res.success && status == ReceiptStatus.FAILED) {
            PhoneNumber fromNum = new PhoneNumber(number:params.From as String)
            List<PhoneNumber> toNums = params.list("remaining")?.collect { Object num ->
                new PhoneNumber(number:num as String)
            } ?: new ArrayList<PhoneNumber>()
            try {
                Map afterPickup = (Helpers.toJson(params.afterPickup) ?: [:]) as Map
                callService
                    .retry(fromNum, toNums, apiId, afterPickup)
                    .logFail("PublicRecordController: retrying call: params: ${params}")
            }
            catch (Throwable e) {
                log.error("PublicRecordController: retry: ${e.message}")
                e.printStackTrace()
            }
        }
        // We don't always immediately store the receipt so sometimes
        // the receipt will not be found. Or, for notification messages
        // we send to a staff member, we are not interested in storing
        // the status of the call or text. If we have a not found error,
        // then catch this and just return an OK status
        if (res.status == ResultStatus.NOT_FOUND) {
            res.logFail("PublicRecordController: could not find receipt", LogLevel.DEBUG)
            respondWithResult(Closure, twimlBuilder.noResponse())
        }
        else { respondWithResult(Closure, res) }
    }
}
