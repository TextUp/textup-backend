package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.WebUtils
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class RequestUtils {

    static final String OWNER_POLICY_ID = "ownerPolicyId"
    static final String PAGINATION_OPTIONS = "paginationOptions"
    static final String PHONE_ID = "phoneId"
    static final String PHONE_RECORD_ID = "phoneRecordId"
    static final String TIMEZONE = "timezone"
    static final String UPLOAD_ERRORS = "uploadErrors"

    static Result<Void> trySetOnRequest(String key, Object obj) {
        try {
            WebUtils.retrieveGrailsWebRequest().currentRequest.setAttribute(key, obj)
            Result.void()
        }
        catch (IllegalStateException e) {
            IOCUtils.resultFactory.failWithThrowable(e, "trySetOnRequest")
        }
    }

    static <T> Result<T> tryGetFromRequest(String key) {
        try {
            HttpServletRequest req = WebUtils.retrieveGrailsWebRequest().currentRequest
            Object obj = req.getAttribute(key) ?: req.getParameter(key)
            // only return a successful result if something is actually found
            if (obj != null) {
                IOCUtils.resultFactory.success(TypeUtils.to(T, obj))
            }
            else {
                IOCUtils.resultFactory.failWithCodeAndStatus("", // TODO
                    ResultStatus.NOT_FOUND, [key])
            }
        }
        catch (IllegalStateException e) {
            IOCUtils.resultFactory.failWithThrowable(e, "tryGetFromRequest")
        }
    }

    static String getBrowserURL(HttpServletRequest req) {
        String browserURL = (req.requestURL.toString() - req.requestURI) +
            getForwardURI(req)
        req.queryString ? "$browserURL?${req.queryString}" : browserURL
    }

    static Result<TypeMap> tryGetJsonBody(HttpServletRequest req, String key = null) {
        try {
            Map json = getRequestJson(req)
            if (key) {
                String requiredRoot = MarshallerUtils.resolveCodeToSingular(key)
                TypeMap.tryCreate(json[requiredRoot])
            }
            else { TypeMap.tryCreate(json) }
        }
        catch (e) { IOCUtils.resultFactory.failWithThrowable(e) }
    }

    // Helpers
    // -------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static String getForwardURI(HttpServletRequest req) {
        req.getForwardURI()
    }

    // (1) Access `request.properties` results in `[Fatal Error] :1:1: Content is not allowed in prolog.`
    // printed to the console each time after the first time this property is accessed. This might
    // be because we only use the JSON property so the XML property is null. We resolve this issue
    // by only accessing the JSON property such that the XML property is never accessed because
    // we are not resolving all properties with `request.properties`.
    // (2) The reason we need to skip type checking here is that Grails dynamically adds
    // additional properties to `HttpServletRequest` and, because these properties are dynamic,
    // they are not added to the interface and the compiler fails
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Map getRequestJson(HttpServletRequest req) {
        req.JSON
    }
}
