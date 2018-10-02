package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsCompileStatic
@RestApi(name="Team", description = "Operations on teams after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class TeamController extends BaseController {

    static String namespace = "v1"

    AuthService authService
    TeamService teamService

    @Override
    protected String getNamespaceAsString() { namespace }

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
            required=true, description="Id of the staff"),
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key to convert times to,
            include schedule intervals, defaults to UTC if unspecified or invalid''')
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
        if (params.timezone) { //for the json marshaller
            request.setAttribute(Constants.REQUEST_TIMEZONE, params.timezone as String)
        }
        Closure<Integer> count
        Closure<List<Team>> list
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
        respondWithMany(Team, count, list, params)
    }

    // Show
    // ----

    @RestApiMethod(description="Show specifics about a team")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the team"),
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key to convert times to,
            include schedule intervals, defaults to UTC if unspecified or invalid''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The requested team was not found."),
        @RestApiError(code="403", description="You do not permissions to view this team.")
    ])
    @Transactional(readOnly=true)
    def show() {
        if (params.timezone) { //for the json marshaller
            request.setAttribute(Constants.REQUEST_TIMEZONE, params.timezone as String)
        }
        Team t1 = Team.get(params.long("id"))
        if (t1) {
            if (authService.hasPermissionsForTeam(t1.id)) {
                respond(t1, [status:ResultStatus.OK.apiStatus])
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    @RestApiMethod(description="Create a new team and associated it with an \
        existing organization")
    @RestApiResponseObject(objectIdentifier = "Team")
    @RestApiBodyObject(name = "Team")
    @RestApiParams(params=[
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key that the dates, including schedule intervals
            passed in are in, defaults to UTC if unspecified or invalid''')
    ])
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
        Map tInfo = getJsonPayload(Team, request)
        if (tInfo == null) { return }
        String tz = params.timezone as String
        if (tz) { //for the json marshaller
            request.setAttribute(Constants.REQUEST_TIMEZONE, tz)
        }
        respondWithResult(Team, teamService.create(tInfo, tz))
    }

    // Update
    // ------

    @RestApiMethod(description="Update an existing team")
    @RestApiResponseObject(objectIdentifier = "Team")
    @RestApiBodyObject(name = "Team")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the team"),
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key that the dates, including schedule intervals
            passed in are in, defaults to UTC if unspecified or invalid''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The requested team was not found."),
        @RestApiError(code="403", description='''The logged in staff member is
            not an admin and so cannot modify teams.'''),
        @RestApiError(code="422", description="The updated fields created an invalid team.")
    ])
    def update() {
        Map tInfo = getJsonPayload(Team, request)
        if (tInfo == null) { return }
        String tz = params.timezone as String
        if (params.timezone) { //for the json marshaller
            request.setAttribute(Constants.REQUEST_TIMEZONE, tz)
        }
        Long id = params.long("id")
        if (authService.exists(Team, id)) {
            if (authService.hasPermissionsForTeam(id)) {
                respondWithResult(Team, teamService.update(id, tInfo, tz))
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
                respondWithResult(Void, teamService.delete(id))
            }
            else { forbidden() }
        }
        else { notFound() }
    }
}
