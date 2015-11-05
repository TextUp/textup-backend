package org.textup.rest

import grails.converters.JSON
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import static org.springframework.http.HttpStatus.*
import org.textup.*
import grails.transaction.Transactional

@RestApi(name="Team", description = "Operations on teams after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class TeamController extends BaseController {

    static namespace = "v1"

    //authService from superclass
    def teamService

    //////////
    // List //
    //////////

    @RestApiMethod(description="List teams", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", paramType=RestApiParamType.QUERY, 
            required=false, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", paramType=RestApiParamType.QUERY, 
            required=false, description="Offset of results"),
        @RestApiParam(name="organizationId", type="Number", paramType=RestApiParamType.QUERY, 
            required=true, description="Id of the organization"),
        @RestApiParam(name="staffId", type="Number", paramType=RestApiParamType.QUERY, 
            required=true, description="Id of the staff")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The organization or staff was not found."),
        @RestApiError(code="400", description="You must specify either an organization id or staff id, but not both."),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (params.organizationId && params.staffId) { badRequest() }
        else if (params.organizationId) {
            Organization org = Organization.get(params.long("organizationId"))
            if (!org) { notFound(); return; }
            if (authService.isAdminAt(org.id)) {
                genericListActionForCriteria(Team, Team.forOrg(org), params)    
            }
            else { forbidden() }
        }
        else if (params.staffId) {
            Staff s1 = Staff.get(params.long("staffId"))
            if (!s1) { notFound(); return; }
            if (authService.isLoggedIn(s1.id) || authService.isAdminAt(s1.org.id)) {
                genericListActionForCriteria(Team, Team.forStaff(s1), params)
            }
            else { forbidden() }
        }
        else { badRequest() }
    }

    //////////
    // Show //
    //////////

    @RestApiMethod(description="Show specifics about a team")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH, 
            description="Id of the team")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The requested team was not found."),
        @RestApiError(code="403", description="You do not permissions to view this team.")
    ])
    @Transactional(readOnly=true)
    def show() { 
        Long id = params.long("id")
        if (Team.exists(id)) {
            if (authService.hasPermissionsForTeam(id)) {
                genericShowAction(Team, id)
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    //////////
    // Save //
    //////////

    @RestApiMethod(description="Create a new team and associated it with an existing organization")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404",description="The organization to add the team to was not found."),
        @RestApiError(code="422", description="The updated fields created an invalid team."),
        @RestApiError(code="403", description="You do not permissions to create a new team for this organization.")
    ])
    def save() {
        if (!validateJsonRequest(request, "team")) { return; }
        Long orgId = Helpers.toLong(request.JSON.team?.org)
        if (authService.exists(Organization, orgId)) {
            if (authService.isAdminAt(orgId)) {
                handleSaveResult(Team, teamService.save(request.JSON.team) )
            }
            else { forbidden() }
        }
        else { notFound() }
    }
    
    
    ////////////
    // Update //
    ////////////

    @RestApiMethod(description="Update an existing team")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH, 
            description="Id of the team")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The requested team was not found."),
        @RestApiError(code="403", description='''The logged in staff member is 
            not an admin and so cannot modify teams.'''),
        @RestApiError(code="422", description="The updated fields created an invalid team.")
    ])
    def update() {
        if (!validateJsonRequest(request, "team")) { return; }
        Long id = params.long("id")
        if (authService.exists(Team, id)) {
            if (authService.isAdminForTeam(id)) {
                handleUpdateResult(Team, teamService.update(id, request.JSON.team))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    ////////////
    // Delete //
    ////////////

    @RestApiMethod(description="Delete an existing team")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH, 
            description="Id of the team")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404", description="The requested team was not found."),
        @RestApiError(code="403", description='''The logged in staff member is 
            not an admin and so cannot delete teams.''')
    ])
    def delete() { 
        Long id = params.long("id")
        if (authService.exists(Team, id)) {
            if (authService.isAdminForTeam(id)) {
                handleDeleteResult(teamService.delete(id))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    /////////////
    // Helpers //
    /////////////

}
