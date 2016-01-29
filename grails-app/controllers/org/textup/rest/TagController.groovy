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

@RestApi(name="Tag", description = "Operations on tags belonging to staff \
    members or teams. Requires logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class TagController extends BaseController {

	static namespace = "v1"

	//authService from superclass
	def tagService

    // List
    // ----

    @RestApiMethod(description="List tags for a specific staff or team", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
        	paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
        	paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="teamId", type="Number", required=true,
        	paramType=RestApiParamType.QUERY, description="Id of the team member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The staff or team was not found. \
            Or, the staff or team is not allowed to have tags."),
        @RestApiError(code="400", description="You must specify either a staff \
            id or team id, but not both."),
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
        genericListActionForClosures("tag", { Map params ->
            p1.phone.countTags()
        }, { Map params ->
            p1.phone.getTags(params)
        }, params)
    }

    // Show
    // ----

    @RestApiMethod(description="Show specifics about a tag")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
        	paramType=RestApiParamType.PATH, description="Id of the tag")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="403", description="You do not have permissions to view this tag."),
        @RestApiError(code="404",  description="The requested tag was not found.")
    ])
    @Transactional(readOnly=true)
    def show() {
    	Long id = params.long("id")
        if (ContactTag.exists(id)) {
            if (authService.hasPermissionsForTag(id)) {
                genericShowAction(ContactTag, id)
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    @RestApiMethod(description="Create a new tag for a staff member or team")
    @RestApiParams(params=[
        @RestApiParam(name="teamId", type="Number", required=true,
        	paramType=RestApiParamType.QUERY, description="Id of the team member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request. Or, you did not specify either staff id or team id (but not both)"),
        @RestApiError(code="422", description="The updated fields created an invalid tag."),
        @RestApiError(code="403", description="You do not permissions to create a new tag for this staff member or team."),
        @RestApiError(code="404",  description="The staff or team member to add this tag to was not found.")
    ])
    def save() {
    	if (!validateJsonRequest(request, "tag")) { return; }
    	Map tagInfo = request.JSON.tag
        if (params.long("teamId")) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.hasPermissionsForTeam(tId)) {
                    handleSaveResult("tag", tagService.createForTeam(tId, tagInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else {
            if (authService.isActive) {
                handleSaveResult("tag", tagService.createForStaff(tagInfo))
            }
            else { forbidden() }
        }
    }

    // Update
    // ------

    @RestApiMethod(description="Update an existing tag")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
        	paramType=RestApiParamType.PATH, description="Id of the tag")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The requested tag was not found."),
        @RestApiError(code="403", description="You do not have permission to modify this tag."),
        @RestApiError(code="422", description="The updated fields created an invalid tag.")
    ])
    def update() {
    	if (!validateJsonRequest(request, "tag")) { return; }
    	Long id = params.long("id")
    	if (authService.exists(ContactTag, id)) {
    		if (authService.hasPermissionsForTag(id)) {
    			handleUpdateResult("tag", tagService.update(id, request.JSON.tag))
			}
			else { forbidden() }
    	}
    	else { notFound() }
    }

    // Delete
    // ------

    @RestApiMethod(description="Delete an existing tag")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
        	description="Id of the tag")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404", description="The requested tag was not found."),
        @RestApiError(code="403", description="You do not have permission to delete this tag.")
    ])
    def delete() {
    	Long id = params.long("id")
    	if (authService.exists(ContactTag, id)) {
    		if (authService.hasPermissionsForTag(id)) {
    			handleDeleteResult(tagService.delete(id))
			}
			else { forbidden() }
    	}
    	else { notFound() }
    }
}
