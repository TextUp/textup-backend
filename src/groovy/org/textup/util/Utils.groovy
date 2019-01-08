package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.codehaus.groovy.grails.web.util.WebUtils
import org.hibernate.*
import org.textup.*
import org.textup.type.*
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

    // Closures
    // --------

    static <T> T callClosure(Closure<T> action, Object[] args) {
        switch (args.length) {
            case 0: return action.call()
            case 1: return action.call(args[0])
            default: return action.call(args)
        }
    }

    // Request
    // -------

    static Result<Void> trySetOnRequest(String key, Object obj) {
        try {
            WebUtils.retrieveGrailsWebRequest().currentRequest.setAttribute(key, obj)
            IOCUtils.resultFactory.success()
        }
        catch (IllegalStateException e) {
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }

    static <T> Result<T> tryGetFromRequest(String key) {
        try {
            HttpServletRequest req = WebUtils.retrieveGrailsWebRequest().currentRequest
            Object obj = req.getAttribute(key) ?: req.getParameter(key)
            IOCUtils.resultFactory.success(TypeConversionUtils.to(T, obj))
        }
        catch (IllegalStateException e) {
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }

    // Properties
    // ----------

    static <T> T withDefault(T val, T defaultVal) {
        val ? val : defaultVal
    }

    // TODO test
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Object getId(Object obj) {
        obj?.metaClass?.hasProperty(obj, "id") ? obj.id : null
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

    static <T extends Saveable> Result<T> trySave(T obj) {
        if (obj.save()) {
            IOCUtils.resultFactory.success(obj)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(obj.errors) }
    }

    static <T extends Saveable> Result<Void> trySaveAllAsResult(Collection<T> objList) {
        ResultGroup<T> resGroup = new ResultGroup<>()
        objList?.each { T obj -> resGroup << Utils.trySave(obj) }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success() }
    }

    // Pagination
    // ----------

    static List<Integer> normalizePagination(Object rawOffset, Object rawMax) {
        Integer offset = TypeConversionUtils.to(Integer, rawOffset),
            max = TypeConversionUtils.to(Integer, rawMax),
            defaultMax = Constants.DEFAULT_PAGINATION_MAX,
            largestMax = Constants.MAX_PAGINATION_MAX
        [
            (offset > 0) ? offset : 0,
            Math.min((max > 0) ? max : defaultMax, largestMax)
        ]
    }
}
