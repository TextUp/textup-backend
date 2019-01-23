package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.WebUtils
import org.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class Utils {

    // Notifications
    // -------------

    static Result<PhoneNumber> tryGetNotificationNumber() {
        String noticeNum = Holders.flatConfig["textup.apiKeys.twilio.notificationNumber"]
        if (!noticeNum) {
            return IOCUtils.resultFactory.failWithCodeAndStatus(
                "utils.getNotificationNumber.missing",
                ResultStatus.INTERNAL_SERVER_ERROR)
        }
        PhoneNumber fromNum = new PhoneNumber(number: noticeNum)
        if (fromNum.validate()) {
            IOCUtils.resultFactory.success(fromNum)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(fromNum.errors) }
    }

    // Properties
    // ----------

    static <T> T withDefault(T val, T defaultVal) {
        val ? val : defaultVal
    }

    static <T extends Number> T inclusiveBound(T val, T min, T max) {
        Math.max(min, Math.min(val, max))
    }

    // Data helpers
    // ------------

    static <T> T doWithoutFlush(Closure<T> doThis) {
        T result
        // doesn't matter which domain class we call this on
        Organization.withSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                result = doThis()
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        result
    }
}
