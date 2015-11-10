package org.textup.rest

import grails.converters.JSON
import grails.gorm.DetachedCriteria
import org.codehaus.groovy.grails.web.json.JSONObject
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*
import org.codehaus.groovy.grails.orm.hibernate.cfg.NamedCriteriaProxy

class BaseController {

    static allowedMethods = [index:"GET", save:"POST", show:"GET", update:"PUT", delete:"DELETE"]
    static responseFormats = ["json"]

    def grailsApplication
    def springSecurityService
    def authService

    //////////////////////
    // Config constants //
    //////////////////////

    private int defaultMax() { grailsApplication.config.textup.defaultMax }
    private int largestMax() { grailsApplication.config.textup.largestMax }
    private def tConfig() { grailsApplication.config.textup }

    /////////////////////
    // Utility methods //
    /////////////////////

    /**
     * Handles pagination logic. Takes in some information as specified below and outputs a map of parameters as detailed below for pagination
     * @param  info Map of necessary information, including:
     *                  params = controller params object possibly containing maximum number of results to return per page and offset of results
     *                  total = total number of results
     *                  next = Map of options to pass to create "next" link
     *                  prev = Map of options to pass to create "prev" link
     * @return Map of pagination info, including:
     *             meta = metainformation including total count, maximum results per page, and offset of results
     *             searchParams = Map of the following:
     *                 max = max number of results
     *                 offset = offset of results
     *             links = prev/next links, if needed
     */
    protected Map handlePagination(Map info) {
        Map params = parseParams(info?.params, info.total),
        	linkParams = (info.linkParams && info.linkParams instanceof Map) ? info.linkParams : [:]
        int max = params.max,
        	offset = params.offset,
        	total = params.total,
        	beforeTotal = total - 1
        //if not default max, then add the custom max as a url param
        if (max != defaultMax()) linkParams << [max:max]

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
            render status:OK
            respond([(label):[], meta:[:]])
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

    protected void respondWithError(def obj, String field, String rejectedValue, String message) {
        JSONObject errorsJson = new JSONObject()
        Collection<Map> errors = []
        errors << [object:obj?.class?.name, field:field, "rejected-value":rejectedValue, message:message]
        errorsJson.put("errors", JSON.parse((errors as JSON).toString()))

        respond errorsJson, [status:UNPROCESSABLE_ENTITY]
    }

    protected def renderAsXml(Closure xml) {
        render([contentType: "text/xml", encoding: "UTF-8"], xml)
    }

    protected Map parseParams(Map params, Integer total) {
        int defaultMax = defaultMax(),
        	largestMax = largestMax(),
        	max = Math.min(params?.int("max") ?: defaultMax, largestMax)
        max = (max > 0) ? max : defaultMax
        total = (total && total > 0) ? total: max

        int offset = params?.int("offset") ?: 0
        [max:max, offset:offset, total:total]
    }

    protected Map mergeIntoParams(Map params, Map searchParams) {
        params.max = searchParams.max
        params.offset = searchParams.offset
        params
    }

    //////////////////////////////
    // Standard methods actions //
    //////////////////////////////

    protected def genericListAction(Class clazz, Map params) {
        genericListAction(getLowercaseSimpleName(clazz), clazz, params)
    }
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

    protected def genericListActionForCriteria(Class clazz, NamedCriteriaProxy criteria, Map params) {
        genericListActionForCriteria(getLowercaseSimpleName(clazz), criteria, params)
    }
    protected def genericListActionForCriteria(String resourceName, NamedCriteriaProxy criteria, Map params) {
        Map linkParams = [namespace:namespace, resource:resourceName, absolute:false],
            options = handlePagination(params:params, total:criteria.count(),
                next:linkParams, prev:linkParams)
        List found = criteria.list(mergeIntoParams(params, options.searchParams))
        withFormat {
            json {
                respondHandleEmpty("v1.${resourceName}.plural", found,
                    [meta:options.meta, links:options.links])
            }
        }
    }

    protected def genericListActionForClosures(Class clazz, Closure count, Closure list, Map params) {
        genericListActionForClosures(getLowercaseSimpleName(clazz), count, list, params)
    }
    protected def genericListActionForClosures(String resourceName, Closure count, Closure list, Map params) {
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

    protected def genericShowAction(Class clazz, Long id) {
        def found = clazz.get(id)
        if (!found) { notFound(); return; }
        withFormat {
            json { respond(found, [status:OK]) }
        }
    }

    protected def handleSaveResult(Class clazz, Result res) {
        handleSaveResult(getLowercaseSimpleName(clazz), res)
    }
    protected def handleSaveResult(String resourceName, Result res) {
        if (res.success) {
            withFormat {
                json {
                    response.addHeader(HttpHeaders.LOCATION,
                        g.createLink(namespace:namespace, resource:resourceName, absolute:true, action:"show", id:res.payload.id))
                    respond res.payload, [status:CREATED]
                }
            }
        }
        else { handleResultFailure(res) }
    }

    protected def handleUpdateResult(Class clazz, Result res) {
        handleUpdateResult(getLowercaseSimpleName(clazz), res)
    }
    protected def handleUpdateResult(String resourceName, Result res) {
        if (res.success) {
            withFormat {
                json {
                    response.addHeader(HttpHeaders.LOCATION,
                        g.createLink(namespace:namespace, resource:resourceName, absolute:true, action:"show", id:res.payload.id))
                    respond res.payload, [status:OK]
                }
            }
        }
        else { handleResultFailure(res) }
    }

    protected def handleDeleteResult(Result res) {
        if (res.success) {
            render status:NO_CONTENT
        }
        else { handleResultFailure(res) }
    }

    protected def handleResultFailure(Result res) {
        switch (res.type) {
            case Constants.RESULT_VALIDATION:
                respond res.payload, [status:UNPROCESSABLE_ENTITY]
                break
            case Constants.RESULT_MESSAGE_STATUS:
                respondWithError(res.payload.message, res.payload.status)
                break
            case Constants.RESULT_THROWABLE:
                respondWithError(res.payload.message, BAD_REQUEST)
                break
            case Constants.RESULT_MESSAGE:
                respondWithError(res.payload.message, BAD_REQUEST)
                break
            default:
                log.error("BaseController.handleResultFailure: result $res has an invalid type!")
                respondWithError(message("baseController.handleResultFailure.error"), INTERNAL_SERVER_ERROR)
        }
    }
    private String getLowercaseSimpleName(Class clazz) {
        String n = clazz.simpleName
        //lowercase first letter
        n[0].toLowerCase() + n.substring(1)
    }

    ////////////////////////////////
    // HttpStatus utility methods //
    ////////////////////////////////

    protected void ok() { render status:OK }
    protected void notFound() { render status:NOT_FOUND }
    protected void forbidden() { render status:FORBIDDEN }
    protected void unauthorized() { render status:UNAUTHORIZED }
    protected void notAllowed() { render status:METHOD_NOT_ALLOWED }
    protected void failsValidation() { render status:UNPROCESSABLE_ENTITY }
    protected void badRequest() { render status:BAD_REQUEST }
    protected void noContent() { render status:NO_CONTENT }
    protected void error() { render status:INTERNAL_SERVER_ERROR }

    /////////////////////
    // Utility methods //
    /////////////////////

    protected boolean validateJsonRequest(req, requiredRoot) {
        try {
            Map json = req.JSON
            if (json."$requiredRoot" == null) {
                badRequest()
                return false
            }
        }
        catch (e) {
            log.debug "BaseController.validateJsonRequest with requiredRoot '$requiredRoot': ${e.message}"
            badRequest()
            return false
        }
        return true
    }

    protected boolean validateJsonRequest(req) {
        try {
            Map json = req.JSON
        }
        catch (e) {
            log.debug "BaseController.validateJsonRequest: ${e.message}"
            badRequest()
            return false
        }
        return true
    }
}
