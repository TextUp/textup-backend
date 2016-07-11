package org.textup.rest

import grails.compiler.GrailsTypeChecked
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.types.ReceiptStatus
import org.textup.types.ResultType
import org.textup.validator.PhoneNumber
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Secured("permitAll")
class PublicRecordController extends BaseController {

    static namespace = "v1"

    //grailsApplication from superclass
    //authService from superclass
    RecordService recordService
    CallbackService callbackService
    TwimlBuilder twimlBuilder
    CallService callService

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }

    def save() {
        callbackService.validate(request, params).then({ ->
            if (params.handle == Constants.CALLBACK_STATUS) {
                String apiId = params.CallSid ?: params.MessageSid
                ReceiptStatus status = params.CallStatus ?
                    ReceiptStatus.translate(params.CallStatus as String) :
                    ReceiptStatus.translate(params.MessageStatus as String)
                Integer duration = Helpers.toInteger(params.CallDuration)
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
                    catch (e) {
                        log.error("PublicRecordController: retry: ${e.message}")
                    }
                }
                // We don't always immediately store the receipt so sometimes
                // the receipt will not be found. Or, for notification messages
                // we send to a staff member, we are not interested in storing
                // the status of the call or text. If we have a not found error,
                // then catch this and just return an OK status
                if (!res.success && res.type == ResultType.MESSAGE_STATUS &&
                    (res.payload as Map).status == NOT_FOUND) {
                    res.logFail("PublicRecordController: could not find receipt")
                    handleXmlResult(twimlBuilder.noResponse())
                }
                else { handleXmlResult(res) }
            }
            else {
                handleXmlResult(callbackService.process(params))
            }
        })
    }
}
