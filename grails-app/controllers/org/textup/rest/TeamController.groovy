package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import static org.springframework.http.HttpStatus.*
import org.textup.*
import grails.transaction.Transactional

@GrailsCompileStatic
@RestApi(name="Team", description = "Operations on teams after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class TeamController extends BaseController {

    static namespace = "v1"

    //authService from superclass
    TeamService teamService

    // List
    // ----

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
        @RestApiError(code="404",description="The organization or staff was \
            not found."),
        @RestApiError(code="400", description="You must specify either an \
            organization id or staff id, but not both."),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        Closure count, list
        if (params.long("organizationId")) {
            Organization org = Organization.get(params.long("organizationId"))
            if (!org) {
                return notFound()
            }
            else if (!authService.isAdminAt(org.id)) {
                return forbidden()
            }
            count = { org.countTeams() }
            list = { Map params -> org.getTeams(params) }
        }
        else {
            Staff s1 = authService.loggedInAndActive
            if (!s1) {
                return forbidden()
            }
            count = { s1.countTeams() }
            list = { Map params -> s1.getTeams(params) }
        }
        genericListActionForClosures(Team, count, list, params)
    }

    // Show
    // ----

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

    // Save
    // ----

    @RestApiMethod(description="Create a new team and associated it with an \
        existing organization")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404",description="The organization to add the team \
            to was not found."),
        @RestApiError(code="422", description="The updated fields created an \
            invalid team."),
        @RestApiError(code="403", description="You do not permissions to \
            create a new team for this organization.")
    ])
    def save() {
        if (!validateJsonRequest(request, "team")) { return }
        Map tInfo = (request.properties.JSON as Map).team as Map
        handleSaveResult(Team, teamService.create(tInfo))
    }

    // Update
    // ------

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
        if (!validateJsonRequest(request, "team")) { return }
        Long id = params.long("id")
        if (authService.exists(Team, id)) {
            if (authService.hasPermissionsForTeam(id)) {
                Map tInfo = (request.properties.JSON as Map).team as Map
                handleUpdateResult(Team, teamService.update(id, tInfo))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Delete
    // ------

    @RestApiMethod(description="Delete an existing team")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the team")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404", description="The requested team was not found."),
        @RestApiError(code="403", description="The logged in staff member is \
            not an admin and so cannot delete teams.")
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
}
