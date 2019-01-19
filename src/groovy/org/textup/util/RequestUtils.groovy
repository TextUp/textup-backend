package org.textup.util

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class RequestUtils {

    static final String TIMEZONE = "timezone"
    static final String UPLOAD_ERRORS = "uploadErrors"
    static final String PAGINATION_OPTIONS = "paginationOptions"
    static final String OWNER_POLICY_ID = "ownerPolicyId"

    static Result<Void> trySetOnRequest(String key, Object obj) {
        try {
            WebUtils.retrieveGrailsWebRequest().currentRequest.setAttribute(key, obj)
            IOCUtils.resultFactory.success()
        }
        catch (IllegalStateException e) {
            IOCUtils.resultFactory.failWithThrowable(e, "trySetOnRequest")
        }
    }

    static <T> Result<T> tryGetFromRequest(String key) {
        try {
            HttpServletRequest req = WebUtils.retrieveGrailsWebRequest().currentRequest
            Object obj = req.getAttribute(key) ?: req.getParameter(key)
            IOCUtils.resultFactory.success(TypeConversionUtils.to(T, obj))
        }
        catch (IllegalStateException e) {
            IOCUtils.resultFactory.failWithThrowable(e, "tryGetFromRequest")
        }
    }

    static String getBrowserURL(HttpServletRequest request) {
        String browserURL = (request.requestURL.toString() - request.requestURI) +
            getForwardURI(request)
        request.queryString ? "$browserURL?${request.queryString}" : browserURL
    }

    // Helpers
    // -------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static String getForwardURI(HttpServletRequest request) {
        request.getForwardURI()
    }
}
