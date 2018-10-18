package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import groovy.transform.TypeCheckingMode
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.orm.hibernate.cfg.NamedCriteriaProxy
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.validator.*

// Methods in this base controller are utility methods abstracting out functionality needed by
// the controllers that extend this base. All methods except for the methods in the helper methods
// section are called by other classes. We have designated all methods are protected in order to
// prevent them from being considered as controller action methods accessible via url

@GrailsCompileStatic
class BaseController {

    static allowedMethods = [index:"GET", save:"POST", show:"GET", update:"PUT", delete:"DELETE"]
    static responseFormats = ["json"]

    GrailsApplication grailsApplication

    // Required method
    // ---------------

    // will be overridden in classes that extend this base class
    // cannot be getNamespace() because that conflicts with the static property of the same name
    protected String getNamespaceAsString() { "v1" }

    // Validate
    // ----------

    protected Map getJsonPayload(HttpServletRequest req) {
        getJsonPayload(null, req)
    }
    protected Map getJsonPayload(Class clazz, HttpServletRequest req) {
        String requiredRoot = clazz ? resolveClassToSingular(clazz) : null
        try {
            Map json = getRequestJson(req)
            if (requiredRoot) {
                if (json == null || json[requiredRoot] == null) {
                    badRequest(); return null;
                }
                else { return Helpers.to(Map, json[requiredRoot]) }
            }
            else { // no required root so just make sure json is not null
                if (json == null) {
                    badRequest(); return null;
                }
                else { return json }
            }
        }
        catch (e) {
            log.debug "BaseController.getJsonPayload with root '$requiredRoot': ${e.message}"
            badRequest()
            return null
        }
    }
    // (1) Access `request.properties` results in `[Fatal Error] :1:1: Content is not allowed in prolog.`
    // printed to the console each time after the first time this property is accessed. This might
    // be because we only use the JSON property so the XML property is null. We resolve this issue
    // by only accessing the JSON property such that the XML property is never accessed because
    // we are not resolving all properties with `request.properties`.
    // (2) The reason we need to skip type checking here is that Grails dynamically adds
    // additional properties to `HttpServletRequest` and, because these properties are dynamic,
    // they are not added to the interface and the compiler fails
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected Map getRequestJson(HttpServletRequest req) {
        req.JSON
    }

    // Respond
    // -------

    protected <T> void respondWithMany(Class<T> clazz, Closure<Integer> count,
        Closure<? extends Collection<T>> list, Map<String,? extends Object> params = [:],
        boolean isPublic = false) {
        Integer max = Helpers.to(Integer, params.max),
            offset = Helpers.to(Integer, params.offset),
            total = count(params)
        Map<String,? extends Object> linkParams = [
                namespace:getNamespaceAsString(),
                resource:resolveClassToResourceName(clazz, isPublic),
                action: "index",
                absolute:false
            ],
            options = handlePagination(max, offset, total, linkParams)
        // ensure that list has reconciled max and offset values
        params.max = (options.meta as Map)?.max
        params.offset = (options.meta as Map)?.offset
        Collection<T> found = list(params)
        // respond appropriately
        render(status:ResultStatus.OK.apiStatus)
        withJsonFormat {
            if (found) {
                respond(found, options)
            }
            else {
                respond(options + [(resolveClassToPlural(clazz)):[]]) } // manually display empty list
        }
    }
    protected <T> void respondWithResult(Class<T> clazz, Result<? extends T> res) {
        if (!res.success) {
            render(status:res.status.apiStatus)
            respond(errors:buildErrorObj([res]))
        }
        else if (clazz == Void || res.status == ResultStatus.NO_CONTENT) {
            render(status:res.status.apiStatus)
        }
        else if (clazz == Closure) {
            render([contentType: "text/xml", encoding: "UTF-8"], res.payload as Closure)
        }
        else {
            withJsonFormat {
                response.addHeader(HttpHeaders.LOCATION,
                    createLink(
                        namespace:getNamespaceAsString(),
                        resource:resolveClassToResourceName(clazz),
                        absolute:true,
                        action:"show",
                        id:getId(res.payload)
                    )
                )
                render(status:res.status.apiStatus)
                respond(res.payload)
            }
        }
    }
    protected <T> void respondWithResult(Class<T> clazz, ResultGroup<T> resGroup) {
        if (resGroup.isEmpty) {
            error()
            return
        }
        Object responseObj = resGroup.anySuccesses ? resGroup.successes*.payload :
            [errors:buildErrorObj(resGroup.failures)]
        HttpStatus thisStatus = resGroup.anySuccesses ? resGroup.successStatus.apiStatus :
            resGroup.failureStatus.apiStatus
        Map<String,? extends Object> responseInfo = [:]
        // if has successes and failures, include the failures in a error object for completeness
        if (resGroup.anySuccesses && resGroup.anyFailures) {
            responseInfo.errors = buildErrorObj(resGroup.failures)
        }
        render(status:thisStatus)
        withJsonFormat {
            if (resGroup.anySuccesses) {
                List<Result<T>> successes = resGroup.successes
                Long resourceId = getId(successes.toArray(new Result<T>[successes.size()])[0]?.payload)
                response.addHeader(HttpHeaders.LOCATION,
                    createLink(
                        namespace:getNamespaceAsString(),
                        resource:resolveClassToResourceName(clazz),
                        absolute:true,
                        action:"show",
                        id:resourceId
                    )
                )
            }
            respond(responseObj, responseInfo)
        }
    }

    // Render statuses
    // ---------------

    protected void ok() {
        render status:ResultStatus.OK.apiStatus
    }
    protected void notFound() {
        render status:ResultStatus.NOT_FOUND.apiStatus
    }
    protected void forbidden() {
        render status:ResultStatus.FORBIDDEN.apiStatus
    }
    protected void unauthorized() {
        render status:ResultStatus.UNAUTHORIZED.apiStatus
    }
    protected void notAllowed() {
        render status:ResultStatus.METHOD_NOT_ALLOWED.apiStatus
    }
    protected void failsValidation() {
        render status:ResultStatus.UNPROCESSABLE_ENTITY.apiStatus
    }
    protected void badRequest() {
        render status:ResultStatus.BAD_REQUEST.apiStatus
    }
    protected void noContent() {
        render status:ResultStatus.NO_CONTENT.apiStatus
    }
    protected void error() {
        render status:ResultStatus.INTERNAL_SERVER_ERROR.apiStatus
    }

    // Skip type-checking helpers
    // --------------------------

    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected void withJsonFormat(Closure doJson) {
        withFormat {
            json(doJson)
        }
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected String createLink(Map<?,?> linkParams) {
        // the generated link must be provided with the CONTROLLER name as the resource
        // NOT the pluralized resource name. Also, must specify the action and for each action
        // must provide all necessary information (e.g., id for show/update/delete)
        g.createLink(linkParams)
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected Long getId(Object obj) {
        Long thisId
        try {
            obj?.id
        }
        catch(Throwable e) {
            log.error("BaseController.getId: obj $obj, with error ${e.message}")
            e.printStackTrace()
        }
        thisId
    }

    // Helper methods
    // --------------

    protected Collection<Map<String,Object>> buildErrorObj(Collection<Result<?>> manyRes) {
        Collection<Map<String,? extends Object>> errors = []
        manyRes.each { Result<?> res ->
            res.errorMessages.each { String errorMsg ->
                errors << [message:errorMsg, statusCode:res.status.intStatus]
            }
        }
        errors
    }

    protected String resolveClassToSingular(Class clazz) {
        String key = resolveClassToConfigKey(clazz),
            code = "textup.rest.${getNamespaceAsString()}.${key}.singular"
        grailsApplication.flatConfig[code] ?: Constants.FALLBACK_SINGULAR
    }
    protected String resolveClassToPlural(Class clazz) {
        String key = resolveClassToConfigKey(clazz),
            code = "textup.rest.${getNamespaceAsString()}.${key}.plural"
        grailsApplication.flatConfig[code] ?: Constants.FALLBACK_PLURAL
    }
    protected String resolveClassToConfigKey(Class clazz) {
        switch (clazz) {
            case AvailablePhoneNumber: return "availableNumber"
            case Contactable: return "contact"
            case ContactTag: return "tag"
            case FeaturedAnnouncement: return "announcement"
            case FutureMessage: return "futureMessage"
            case IncomingSession: return "session"
            case Location: return "location"
            case MediaElement: return "mediaElement"
            case MediaInfo: return "mediaInfo"
            case MergeGroup: return "mergeGroup"
            case Notification: return "notification"
            case NotificationStatus: return "notificationStatus"
            case Organization: return "organization"
            case Phone: return "phone"
            case RecordItem: return "record"
            case RecordItemStatus: return "recordItemStatus"
            case RecordNoteRevision: return "revision"
            case Schedule: return "schedule"
            case Staff: return "staff"
            case Team: return "team"
            default: return "result"
        }
    }
    protected String resolveClassToResourceName(Class clazz, boolean isPublic = false) {
        switch (clazz) {
            case AvailablePhoneNumber: return "number"
            case Contactable: return "contact"
            case ContactTag: return "tag"
            case FeaturedAnnouncement: return "announcement"
            case FutureMessage: return "futureMessage"
            case IncomingSession: return "session"
            case Notification: return "notify"
            case Organization: return isPublic ? "publicOrganization" : "organization"
            case RecordItem: return "record" // will never return records via public API
            case Staff: return isPublic ? "publicStaff" : "staff"
            case Team: return "team"
            default: return "resource"
        }
    }

    protected Map<String,? extends Object> handlePagination(Integer optMax, Integer optOffset,
        Integer optTotal, Map<String,? extends Object> linkParams = [:]) {
        List<Integer> paginationOptions = buildPaginationOptions(optMax, optOffset, optTotal)
        Integer max = paginationOptions[0]
        Integer offset = paginationOptions[1]
        Integer total = paginationOptions[2]
        Map<String,String> links = [:]

        // if there is an offset, then we need to include previous link
        if (offset > 0) {
            int prevOffset = offset - max
            prevOffset = (prevOffset >= 0) ? prevOffset : 0
            links.prev = createLink(linkParams + [params:[max:max, offset:prevOffset]])
        }
        // if there are more results that to display, include next link
        if ((offset + max) < total) {
            int nextOffset = offset + max
            links.next = createLink(linkParams + [params:[max:max, offset:nextOffset]])
        }
        Map<String,? extends Object> results = [meta:[max:max, offset:offset, total:total]]
        if (links) {
            results.links = links
        }
        results
    }
    protected List<Integer> buildPaginationOptions(Integer optMax, Integer optOffset, Integer optTotal) {
        Integer defaultMax = Constants.DEFAULT_PAGINATION_MAX,
            largestMax = Constants.MAX_PAGINATION_MAX,
            offset = (optOffset > 0) ? optOffset : 0,
            max = Math.min((optMax > 0) ? optMax : defaultMax, largestMax),
            total = (optTotal >= 0) ? optTotal : max
        [max, offset, total]
    }
}
