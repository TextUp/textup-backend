package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.orm.hibernate.cfg.NamedCriteriaProxy
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class BaseController {

    void index() { notAllowed() }

    void show() { notAllowed() }

    void save() { notAllowed() }

    void update() { notAllowed() }

    void delete() { notAllowed() }

    // Helpers
    // -------

    protected void respondWithCriteria(DetachedCriteria<?> criteria, Map params = [:],
        Closure sortOptions = null, String customMarshallerKey = null) {

        respondWithMany(CriteriaUtils.countAction(criteria),
            { Map opts ->
                sortOptions ? criteria.build(sortOptions).list(opts) : criteria.list(opts)
            },
            params,
            customMarshallerKey)
    }

    protected void respondWithMany(Closure<Integer> count, Closure<? extends Collection<?>> list,
        Map params = [:], String customMarshallerKey = null) {
        // step 1: build info
        Integer total = count.call()
        Map<String, Integer> pg1 = ControllerUtils.buildPagination(params, total)
        Map<String, ?> lInfo = [resource: controllerName, action: RestUtils.ACTION_GET_LIST, absolute: false]
        Map<String, String> links = ControllerUtils.buildLinks(lInfo, pg1.offset, pg1.max, pg1.total)
        Collection<?> found = list.call(pg1)
        // step 2: build response
        Map<String, ?> responseData = [
            (MarshallerUtils.PARAM_LINKS): links,
            (MarshallerUtils.PARAM_META): pg1
        ]
        render(status: ResultStatus.OK.apiStatus)
        withJsonFormat {
            if (found) {
                respond(found, responseData)
            }
            else { // manually display empty list
                String rootKey = MarshallerUtils.resolveCodeToPlural(customMarshallerKey)
                respond([(rootKey): []] + responseData)
            }
        }
    }

    protected void doShow(Closure<Result<?>> checkAllowed, Closure<Result<?>> doFind) {
        checkAllowed()
            .then { doFind() }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }

    protected void doSave(String key, HttpServletRequest req, ManagesDomain.Creater service,
        Closure<Result<Long>> checkAllowed) {

        RequestUtils.tryGetJsonBody(req, key)
            .then { TypeMap body -> checkAllowed(body).curry(body) }
            .then { TypeMap body, Long id -> service.create(id, body) }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }

    protected void doUpdate(String key, HttpServletRequest req, ManagesDomain.Updater service,
        Closure<Result<Long>> checkAllowed) {

        RequestUtils.tryGetJsonBody(req, key)
            .then { TypeMap body -> checkAllowed(body).curry(body) }
            .then { TypeMap body, Long id -> service.update(id, body) }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }

    protected void doDelete(ManagesDomain.Deleter service, Closure<Result<Long>> checkAllowed) {
        checkAllowed()
            .then { Long id -> service.delete(id) }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }

    protected void respondWithResult(Result<?> res) {
        // step 1: status
        render(status: res.status.apiStatus)
        // step 2: payload, if any
        if (res.success) {
            if (res.payload instanceof Closure) {
                Map<String, String> info = [
                    contentType: ControllerUtils.CONTENT_TYPE_XML,
                    encoding: Constants.DEFAULT_CHAR_ENCODING
                ]
                render(info, res.payload)
            }
            else if (res.payload && res.status != ResultStatus.NO_CONTENT) {
                withJsonFormat {
                    Object idObj = DomainUtils.tryGetId(res.payload)
                    if (idObj) {
                        String locationLink = IOCUtils.linkGenerator.link(resource: controllerName,
                            absolute: true,
                            action: RestUtils.ACTION_GET_SINGLE,
                            id: DomainUtils.getId(res.payload))
                        response.addHeader(HttpHeaders.LOCATION, locationLink)
                    }
                    respond(res.payload)
                }
            }
        }
        else { respond(CollectionUtils.buildErrors(res)) }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected void withJsonFormat(Closure doJson) {
        withFormat { json(doJson) }
    }
}
