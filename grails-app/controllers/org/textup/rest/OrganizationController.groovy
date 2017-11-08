package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.OrgStatus

@GrailsCompileStatic
@RestApi(name="Organization", description = "Operations on organizations after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class OrganizationController extends BaseController {

	static String namespace = "v1"

    AuthService authService
    OrganizationService organizationService

    @Override
    protected String getNamespaceAsString() { namespace }

    // List
    // ----

    @RestApiMethod(description="List organizations", listing=true)
    @RestApiResponseObject(objectIdentifier = "Organization")
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="search", type="String", required=false,
            paramType=RestApiParamType.QUERY, description='''String to search for in
            organization\'s name and address'''),
        @RestApiParam(name="status[]", type="List", paramType=RestApiParamType.QUERY,
            allowedvalues=["rejected", "pending", "approved"],
            required=false, description='''List of organization statuses to restrict to.
            Default showing approved. Ignored if search query param specified'''),
    ])
    @Transactional(readOnly=true)
    def index() {
        Closure<Integer> count = { Organization.count() }
        Closure<List<Organization>> list = { Map params -> Organization.list(params) }
        if (params.search) {
            String query = Helpers.toQuery(params.search)
            count = { Organization.countSearch(query) }
            list = { Map params -> Organization.search(query, params) }
        }
        else {
            List<OrgStatus> statusEnums = Helpers.toEnumList(OrgStatus, params.list("status[]"))
            if (statusEnums) {
                count = { Organization.countByStatusInList(statusEnums) }
                list = { Map params -> Organization.findAllByStatusInList(statusEnums, params) }
            }
        }
        respondWithMany(Organization, count, list, params, !authService.isActive)
    }

    // Show
    // ----

    @RestApiMethod(description="Show specifics about an organization")
    @RestApiResponseObject(objectIdentifier = "Organization")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
            paramType=RestApiParamType.PATH, description="Id of the organization")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404", description="The organization was not found.")
    ])
    @Transactional(readOnly=true)
    def show() {
        Organization org1 = Organization.get(params.long("id"))
        if (org1) {
            respond(org1, [status:ResultStatus.OK.apiStatus])
        }
        else { notFound() }
    }

    // Save
    // ----

    def save() {
        notAllowed()
    }

    // Update
    // ------

    @RestApiMethod(description="Update an existing organization")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the organization")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="403", description='''The logged in staff member is not an admin at this
            organization and so cannot make any changes.'''),
        @RestApiError(code="404", description="The organization was not found."),
        @RestApiError(code="422", description="The updated fields created an invalid organization.")
    ])
    def update() {
        if (!validateJsonRequest(Organization, request)) { return }
        Long id = params.long("id")
        if (authService.exists(Organization, id)) {
            if (!authService.isAdminAt(id)) {
                return forbidden()
            }
            Map oInfo = (request.properties.JSON as Map).organization as Map
            respondWithResult(Organization, organizationService.update(id, oInfo))
        }
        else { notFound() }
    }

    // Delete
    // ------

    def delete() { notAllowed() }
}
