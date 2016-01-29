package org.textup.rest

import grails.converters.JSON
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.restapidoc.annotation.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*
import org.restapidoc.pojo.*
import grails.transaction.Transactional

@RestApi(name="Organization", description = "Operations on organizations after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class OrganizationController extends BaseController {

	static namespace = "v1"

    //authService from superclass
    def organizationService

    //////////
    // List //
    //////////

    @RestApiMethod(description="List organizations", listing=true)
    @RestApiResponseObject(objectIdentifier = "Organization")
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="search", type="String", required=false,
            paramType=RestApiParamType.QUERY, description='''String to search for in
            organization\'s name and address''')
    ])
    @Transactional(readOnly=true)
    def index() {
        if (params.search) {
            String query = Helpers.toQuery(params.search)
            genericListActionForClosures(Organization, {
                Organization.countSearch(query)
            }, { Map params ->
                Organization.search(query, params)
            }, params)
        }
        else {
            genericListAction(Organization, params)
        }
    }

    //////////
    // Show //
    //////////

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
        genericShowAction(Organization, params.long("id"))
    }

    //////////
    // Save //
    //////////

    def save() {
        notAllowed()
    }

    ////////////
    // Update //
    ////////////

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
        if (!validateJsonRequest(request, "organization")) { return }
        Long id = params.long("id")
        if (authService.exists(Organization, id)) {
            if (!authService.isAdminAt(id)) {
                return forbidden()
            }
            Map oInfo = request.JSON.organization
            handleUpdateResult(Organization,
                organizationService.update(params.long("id"), oInfo))
        }
        else { notFound() }
    }

    ////////////
    // Delete //
    ////////////

    def delete() { notAllowed() }
}
