package org.textup.rest

import grails.compiler.GrailsTypeChecked
import java.util.concurrent.TimeUnit
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsTypeChecked
@Secured("permitAll")
class PublicRecordController extends BaseController {

    static String namespace = "v1"

    //grailsApplication from superclass
    CallbackService callbackService
    CallbackStatusService callbackStatusService
    ThreadService threadService

    @Override
    protected String getNamespaceAsString() { namespace }

    def index() { notAllowed() }
    def show() { notAllowed() }
    def update() { notAllowed() }
    def delete() { notAllowed() }

    def save() {
        TypeConvertingMap paramsMap = TypeConversionUtils.extractParams(params)
        Result<Void> res = TwilioUtils.validate(request, paramsMap)
        if (!res.success) {
            respondWithResult(Void, res)
        }
        else if (paramsMap.handle == Constants.CALLBACK_STATUS) {
            // Moved creation of new thread to PublicRecordController to avoid self-calls.
            // Aspect advice is not applied on self-calls because this bypasses the proxies Spring AOP
            // relies on. See https://docs.spring.io/spring/docs/3.1.x/spring-framework-reference/html/aop.html#aop-understanding-aop-proxies
            threadService.delay(5, TimeUnit.SECONDS) { callbackStatusService.process(paramsMap) }
            respondWithResult(Closure, TwilioUtils.noResponseTwiml())
        }
        else { respondWithResult(Closure, callbackService.process(paramsMap)) }
    }
}
