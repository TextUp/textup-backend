package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
@RestApi(name="Announcement", description="Operations on announcements, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class AnnouncementController extends BaseController {

	static String namespace = "v1"

    AuthService authService
    AnnouncementService announcementService

    @Override
    protected String getNamespaceAsString() { namespace }

    // List
    // ----

    @RestApiMethod(description="List announcement for a specific staff or team", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="teamId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the team member"),
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description='''The staff or team was not found. Or, the
            staff or team specified is not allowed to have announcement.'''),
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
        DetachedCriteria<FeaturedAnnouncement> query = FeaturedAnnouncement.forPhoneId(p1.id)
        respondWithMany(FeaturedAnnouncement, { query.count() }, { Map p -> query.list(p) }, params)
    }

    // Show
    // ----

    @RestApiMethod(description="Show specifics about a announcement")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
            paramType=RestApiParamType.PATH, description="Id of the announcement")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="403", description="You do not have permissions \
            to view this announcement."),
        @RestApiError(code="404",  description="The requested announcement was not found.")
    ])
    @Transactional(readOnly=true)
    def show() {
        FeaturedAnnouncement announce1 = FeaturedAnnouncement.get(params.long("id"))
        if (announce1) {
            if (authService.hasPermissionsForAnnouncement(announce1.id)) {
                respond(announce1, [status:ResultStatus.OK.apiStatus])
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    @RestApiMethod(description="Create a new announcement for staff member or team")
    @RestApiParams(params=[
        @RestApiParam(name="teamId", type="Number",
            paramType=RestApiParamType.QUERY, description="Id of the team member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="422", description="The updated fields created an \
            invalid announcement."),
        @RestApiError(code="403", description="You do not permissions to create \
            a new announcement for this team."),
        @RestApiError(code="404",  description="The team member to \
            add this announcement to was not found.")
    ])
    def save() {
        Map aInfo = getJsonPayload(FeaturedAnnouncement, request)
        if (aInfo == null) { return }
        if (params.long("teamId")) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.hasPermissionsForTeam(tId)) {
                    respondWithResult(FeaturedAnnouncement,
                        announcementService.createForTeam(tId, aInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else {
            if (authService.isActive) {
                respondWithResult(FeaturedAnnouncement, announcementService.createForStaff(aInfo))
            }
            else { forbidden() }
        }
    }

    // Update
    // ------

    @RestApiMethod(description="Update an existing announcement")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
            paramType=RestApiParamType.PATH, description="Id of the announcement")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The requested announcement was not found."),
        @RestApiError(code="403", description="You do not have permission to \
            modify this announcement."),
        @RestApiError(code="422", description="The updated fields created an \
            invalid announcement.")
    ])
    def update() {
        Map aInfo = getJsonPayload(FeaturedAnnouncement, request)
        if (aInfo == null) { return }
    	Long id = params.long("id")
    	if (authService.exists(FeaturedAnnouncement, id)) {
    		if (authService.hasPermissionsForAnnouncement(id)) {
    			respondWithResult(FeaturedAnnouncement, announcementService.update(id, aInfo))
			}
			else { forbidden() }
    	}
    	else { notFound() }
    }

    // Delete
    // ------

    def delete() { notAllowed() }
}
