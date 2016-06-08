package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.gorm.DetachedCriteria
import grails.plugin.springsecurity.SpringSecurityService
import groovy.transform.TypeCheckingMode
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.orm.hibernate.cfg.NamedCriteriaProxy
import org.codehaus.groovy.grails.web.json.JSONObject
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.http.HttpStatus
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.types.ResultType
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
class BaseController {

    static allowedMethods = [index:"GET", save:"POST", show:"GET",
        update:"PUT", delete:"DELETE"]
    static responseFormats = ["json"]

    GrailsApplication grailsApplication
    SpringSecurityService springSecurityService
    AuthService authService

    // Config constants
    // ----------------

    protected Integer getDefaultMax() {
        Helpers.toInteger(grailsApplication.flatConfig["textup.defaultMax"])
    }
    protected Integer getLargestMax() {
        Helpers.toInteger(grailsApplication.flatConfig["textup.largestMax"])
    }

    // Pagination
    // ----------

    /**
     * Handles pagination logic. Takes in some information as specified below
     *     and outputs a map of parameters as detailed below for pagination
     * @param  info Map of necessary information, including:
     *                  params = controller params object possibly containing
     *                      maximum number of results to return per page and offset of results
     *                  total = total number of results
     *                  next = Map of options to pass to create "next" link
     *                  prev = Map of options to pass to create "prev" link
     * @return Map of pagination info, including:
     *             meta = metainformation including total count, maximum results
     *                 per page, and offset of results
     *             searchParams = Map of the following:
     *                 max = max number of results
     *                 offset = offset of results
     *             links = prev/next links, if needed
     */
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected Map handlePagination(Map info) {
        Map params = parseParams(info?.params, Helpers.toInteger(info.total)),
        	linkParams = (info.linkParams && info.linkParams instanceof Map) ?
                info.linkParams : [:]
        Integer max = params.max,
        	offset = params.offset,
        	total = params.total,
        	beforeTotal = total - 1
        //if not default max, then add the custom max as a url param
        if (max != this.defaultMax) {
            linkParams << [max:max]
        }
        Map<String,String> links = [:]
        if (offset > 0) {
            int newOffset = offset - max
            linkParams.offset = (newOffset >= 0) ? newOffset : 0
            links << [prev:g.createLink(info?.prev + [params:linkParams])]
        }
        if ((offset + max) <= beforeTotal) {
            linkParams.offset = offset + max
            links << [next:g.createLink(info?.next + [params:linkParams])]
        }
        def results = [:]
        results.meta = [total:total, max:max, offset:offset]
        results.searchParams = [max:max, offset:offset]
        if (links) results.links = links

        results
    }
    protected Map parseParams(def params, Integer total) {
        Integer defaultMax = this.defaultMax,
            largestMax = this.largestMax,
            pMax, pOffset
        if (params instanceof GrailsParameterMap) {
            GrailsParameterMap gMap = params as GrailsParameterMap
            pMax = gMap?.int("max") ?:  defaultMax
            pOffset = gMap?.int("offset") ?: 0
        }
        else { pOffset = 0 }
        Integer max = Math.min(pMax, largestMax)
        max = (max > 0) ? max : defaultMax
        total = (total >= 0) ? total: max
        [max:max, offset:pOffset, total:total]
    }
    protected Map mergeIntoParams(Map params, Map searchParams) {
        params.max = searchParams.max
        params.offset = searchParams.offset
        params
    }

    // Respond helpers
    // ---------------

    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected void respondHandleEmpty(String ifEmpty, def obj, Map params) {
        if (obj) { respond(obj, params + [status:OK]) }
        else {
            Collection<String> path = ifEmpty.tokenize(".")
            def target = grailsApplication.config.textup.rest
            for (step in path) {
                if (target != null) { target = target."${step}" }
                else { break }
            }
            String label = (target && target instanceof String) ? target : "result"
            if (!params.meta) {
                params.meta = [total:0]
            } else if (params.meta.total == null) {
                params.meta.total = 0
            }
            params[label] = []
            render status:OK
            respond(params)
        }
    }
    protected void respondWithError(String message, HttpStatus status) {
        respondWithErrors([message], status)
    }
    protected void respondWithErrors(Collection<String> messages, HttpStatus status) {
        JSONObject errorsJson = new JSONObject()
        Collection<Map> errors = []
        messages.each { String message -> errors << [message:message] }
        errorsJson.put("errors", JSON.parse((errors as JSON).toString()))

        respond errorsJson, [status:status]
    }
    protected void respondWithError(def obj, String field, String
        rejectedValue, String message) {
        JSONObject errorsJson = new JSONObject()
        Collection<Map> errors = []
        errors << [object:obj?.class?.name, field:field,
            "rejected-value":rejectedValue, message:message]
        errorsJson.put("errors", JSON.parse((errors as JSON).toString()))

        respond errorsJson, [status:UNPROCESSABLE_ENTITY]
    }
    protected def renderAsXml(Closure xml) {
        render([contentType: "text/xml", encoding: "UTF-8"], xml)
    }
    protected def handleXmlResult(Result<Closure> res) {
        if (res.success) {
            renderAsXml(res.payload)
        }
        else { handleResultFailure(res) }
    }
    protected def handleResultWithStatus(Result res, HttpStatus status) {
        if (res.success) { render status:status }
        else { handleResultFailure(res) }
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected def handleResultFailure(Result res) {
        switch (res.type) {
            case ResultType.VALIDATION:
                respondWithErrors(res.errorMessages, UNPROCESSABLE_ENTITY)
                break
            case ResultType.MESSAGE_STATUS:
                respondWithErrors(res.errorMessages, res.payload.status)
                break
            case ResultType.MESSAGE_LIST_STATUS:
                respondWithErrors(res.errorMessages, res.payload.status)
                break
            case ResultType.THROWABLE:
                respondWithErrors(res.errorMessages, BAD_REQUEST)
                break
            case ResultType.MESSAGE:
                respondWithErrors(res.errorMessages, BAD_REQUEST)
                break
            default:
                log.error("BaseController.handleResultFailure: \
                    result $res has an invalid type!")
                respondWithError(message("baseController.handleResultFailure.error"),
                    INTERNAL_SERVER_ERROR)
        }
    }
    protected def handleResultListFailure(HttpStatus errorCode, ResultList resList) {
        respond resList.failures*.errorMessages.flatten(), [status:errorCode]
    }

    // List
    // ----

    protected def genericListAction(Class clazz, Map params) {
        genericListAction(getLowercaseSimpleName(clazz), clazz, params)
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected def genericListAction(String resourceName, Class clazz, Map params) {
        Map linkParams = [namespace:namespace, resource:resourceName, absolute:false],
            options = handlePagination(params:params, total:clazz.count(),
                next:linkParams, prev:linkParams)
        List found = clazz.list(options.searchParams)
        withFormat {
            json {
                respondHandleEmpty("v1.${resourceName}.plural", found,
                    [meta:options.meta, links:options.links])
            }
        }
    }
    protected def genericListActionAllResults(Class clazz, Collection results) {
        genericListActionAllResults(getLowercaseSimpleName(clazz), clazz, results)
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected def genericListActionAllResults(String resourceName, Class clazz,
        Collection results) {
        int numRes = results.size()
        withFormat {
            json {
                respondHandleEmpty("v1.${resourceName}.plural", results,
                    [meta:[total:numRes, max:numRes, offset:numRes]])
            }
        }
    }
    protected def genericListActionForClosures(Class clazz, Closure count, Closure
        list, Map params) {
        genericListActionForClosures(getLowercaseSimpleName(clazz), count, list, params)
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected def genericListActionForClosures(String resourceName, Closure count,
        Closure list, Map params) {
        Map linkParams = [namespace:namespace, resource:resourceName, absolute:false],
            options = handlePagination(params:params, total:count(params),
                next:linkParams, prev:linkParams)
        List found = list(mergeIntoParams(params, options.searchParams))
        withFormat {
            json {
                respondHandleEmpty("v1.${resourceName}.plural", found,
                    [meta:options.meta, links:options.links])
            }
        }
    }

    // Show
    // ----

    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected def genericShowAction(Class clazz, Long id) {
        def found = clazz.get(id)
        if (!found) { notFound(); return; }
        withFormat {
            json { respond(found, [status:OK]) }
        }
    }

    // Save
    // ----

    protected def handleSaveResult(Class clazz, Result res) {
        handleSaveResult(getLowercaseSimpleName(clazz), res)
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected def handleSaveResult(String resourceName, Result res) {
        if (res.success) {
            withFormat {
                json {
                    response.addHeader(HttpHeaders.LOCATION,
                        g.createLink(namespace:namespace, resource:resourceName,
                            absolute:true, action:"show", id:res.payload.id))
                    respond res.payload, [status:CREATED]
                }
            }
        }
        else { handleResultFailure(res) }
    }
    protected def handleResultListForSave(Class clazz, ResultList resList) {
        handleResultListForSave(getLowercaseSimpleName(clazz), resList)
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected def handleResultListForSave(String resourceName, ResultList resList) {
        if (resList.isAnySuccess) {
            withFormat {
                json {
                    response.addHeader(HttpHeaders.LOCATION,
                        g.createLink(namespace:namespace, resource:resourceName,
                            absolute:true, action:"show", id:resList.successes[0].payload.id))
                    respond resList.successes*.payload, [status:CREATED]
                }
            }
        }
        else { handleResultListFailure(UNPROCESSABLE_ENTITY, resList) }
    }

    // Update
    // ------

    protected def handleUpdateResult(Class clazz, Result res) {
        handleUpdateResult(getLowercaseSimpleName(clazz), res)
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    protected def handleUpdateResult(String resourceName, Result res) {
        if (res.success) {
            withFormat {
                json {
                    response.addHeader(HttpHeaders.LOCATION,
                        g.createLink(namespace:namespace, resource:resourceName,
                            absolute:true, action:"show", id:res.payload.id))
                    respond res.payload, [status:OK]
                }
            }
        }
        else { handleResultFailure(res) }
    }

    // Delete
    // ------

    protected def handleDeleteResult(Result res) {
        if (res.success) {
            render status:NO_CONTENT
        }
        else { handleResultFailure(res) }
    }
    protected String getLowercaseSimpleName(Class clazz) {
        String n = clazz.simpleName
        //lowercase first letter
        n[0].toLowerCase() + n.substring(1)
    }

    // HttpStatus
    // ----------

    protected void ok() {
        render status:OK
    }
    protected void notFound() {
        render status:NOT_FOUND
    }
    protected void forbidden() {
        render status:FORBIDDEN
    }
    protected void unauthorized() {
        render status:UNAUTHORIZED
    }
    protected void notAllowed() {
        render status:METHOD_NOT_ALLOWED
    }
    protected void failsValidation() {
        render status:UNPROCESSABLE_ENTITY
    }
    protected void badRequest() {
        render status:BAD_REQUEST
    }
    protected void noContent() {
        render status:NO_CONTENT
    }
    protected void error() {
        render status:INTERNAL_SERVER_ERROR
    }

    // Utility methods
    // ---------------

    protected boolean validateJsonRequest(HttpServletRequest req, String requiredRoot) {
        try {
            Map json = req.properties.JSON as Map
            if (json[requiredRoot] == null) {
                badRequest()
                return false
            }
        }
        catch (e) {
            log.debug "BaseController.validateJsonRequest with requiredRoot \
                '$requiredRoot': ${e.message}"
            badRequest()
            return false
        }
        return true
    }

    protected boolean validateJsonRequest(HttpServletRequest req) {
        try {
            Map json = req.properties.JSON as Map
            if (json == null) {
                badRequest()
                return false
            }
        }
        catch (e) {
            log.debug "BaseController.validateJsonRequest: ${e.message}"
            badRequest()
            return false
        }
        return true
    }
}
