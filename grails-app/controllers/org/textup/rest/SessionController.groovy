package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@RestApi(name="Session", description="Operations on sessions, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class SessionController extends BaseController {

	static namespace = "v1"

    // authService from superclass
    SessionService sessionService

    // List
    // ----

    @RestApiMethod(description="List sessions for a specific staff or team", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="subscribed", type="String", required=true,
            paramType=RestApiParamType.QUERY, description='''One of call or text.
            Show only those who are subscribed to this medium.'''),
        @RestApiParam(name="teamId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the team member"),
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description='''The staff or team was not found. Or, the
            staff or team specified is not allowed to have sessions.'''),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
    	Phone p1
        if (params.teamId) {
            Team t1 = Team.get(params.long("teamId"))
            if (!t1 || !t1.phone) {
                return notFound()
            }
            else if (!authService.hasPermissionsForTeam(t1.id)) {
                return forbidden()
            }
            p1 = t1.phone
        }
        else {
            Staff s1 = authService.loggedInAndActive
            if (!s1 || !s1.phone) {
                return forbidden()
            }
            p1 = s1.phone
        }
        Closure count, list
        if (params.subscribed == "call") {
        	count = { Map params -> p1.countCallSubscribedSessions() }
        	list = { Map params -> p1.getCallSubscribedSessions(params) }
        }
        else if (params.subscribed == "text") {
        	count = { Map params -> p1.countTextSubscribedSessions() }
        	list = { Map params -> p1.getTextSubscribedSessions(params) }
        }
        else {
        	count = { Map params -> p1.countSessions() }
        	list = { Map params -> p1.getSessions(params) }
        }
        genericListActionForClosures("session", count, list, params)
    }

    // Show
    // ----

    @RestApiMethod(description="Show specifics about a session")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
            paramType=RestApiParamType.PATH, description="Id of the session")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="403", description="You do not have permissions to view this session."),
        @RestApiError(code="404",  description="The requested session was not found.")
    ])
    @Transactional(readOnly=true)
    def show() {
    	Long id = params.long("id")
        if (IncomingSession.exists(id)) {
            if (authService.hasPermissionsForSession(id)) {
                genericShowAction(IncomingSession, id)
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    @RestApiMethod(description="Create a new session for staff member or team")
    @RestApiParams(params=[
        @RestApiParam(name="teamId", type="Number",
            paramType=RestApiParamType.QUERY, description="Id of the team member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="422", description="The updated fields created an invalid session."),
        @RestApiError(code="403", description="You do not permissions to create \
            a new session for this team."),
        @RestApiError(code="404",  description="The team member to \
            add this session to was not found.")
    ])
    def save() {
    	if (!validateJsonRequest(request, "session")) { return }
    	Map sessInfo = (request.properties.JSON as Map).session as Map
        if (params.long("teamId")) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.hasPermissionsForTeam(tId)) {
                    handleSaveResult("session",
                    	sessionService.createForTeam(tId, sessInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else {
            if (authService.isActive) {
                handleSaveResult("session", sessionService.createForStaff(sessInfo))
            }
            else { forbidden() }
        }
    }

    // Update
    // ------

    @RestApiMethod(description="Update an existing session")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
            paramType=RestApiParamType.PATH, description="Id of the session")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The requested session was not found."),
        @RestApiError(code="403", description="You do not have permission to modify this session."),
        @RestApiError(code="422", description="The updated fields created an invalid session.")
    ])
    def update() {
    	if (!validateJsonRequest(request, "session")) { return }
    	Long id = params.long("id")
        Map sessInfo = (request.properties.JSON as Map).session as Map
    	if (authService.exists(IncomingSession, id)) {
    		if (authService.hasPermissionsForSession(id)) {
    			handleUpdateResult("session", sessionService.update(id, sessInfo))
			}
			else { forbidden() }
    	}
    	else { notFound() }
    }

    // Delete
    // ------

    def delete() { notAllowed() }
}