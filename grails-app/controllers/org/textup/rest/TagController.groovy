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

@RestApi(name="Tag", description = "Operations on tags belonging to staff members or teams. Requires logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class TagController extends BaseController {

	static namespace = "v1"

	//authService from superclass
	def tagService

    //////////
    // List //
    //////////

    @RestApiMethod(description="List tags for a specific staff or team", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false, allowedvalues="YOUR MOM",
        	paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
        	paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="staffId", type="Number", required=true,
        	paramType=RestApiParamType.QUERY, description="Id of the staff member"),
        @RestApiParam(name="teamId", type="Number", required=true,
        	paramType=RestApiParamType.QUERY, description="Id of the team member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The staff or team was not found. Or, the staff or team is not allowed to have tags."),
        @RestApiError(code="400", description="You must specify either a staff id or team id, but not both."),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (params.staffId && params.teamId) { badRequest() }
        else if (params.staffId) {
        	Staff s1 = Staff.get(params.long("staffId"))
        	if (!s1 || !s1.phone) { notFound() }
        	else if (authService.isLoggedIn(s1.id)) {
        		genericListActionForCriteria("tag", ContactTag.forStaff(s1), params)
        	}
        	else { forbidden() }
        }
        else if (params.teamId) {
        	Team t1 = Team.get(params.long("teamId"))
        	if (!t1 || !t1.phone) { notFound() }
        	else if (authService.belongsToSameTeamAs(t1.id)) {
        		genericListActionForCriteria("tag", ContactTag.forTeam(t1), params)
        	}
        	else { forbidden() }
        }
        else { badRequest() }
    }

    //////////
    // Show //
    //////////

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
            if (authService.hasPermissionsForTag(id)) { genericShowAction(ContactTag, id) }
            else { forbidden() }
        }
        else { notFound() }
    }

    //////////
    // Save //
    //////////

    @RestApiMethod(description="Create a new tag for a staff member or team")
    @RestApiParams(params=[
        @RestApiParam(name="staffId", type="Number", required=true,
        	paramType=RestApiParamType.QUERY, description="Id of the staff member"),
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
    	if (params.staffId && params.teamId) { badRequest() }
    	else if (params.staffId) {
    		Long sId = params.long("staffId")
    		if (authService.exists(Staff, sId)) {
    			if (authService.isLoggedIn(sId)) {
    				handleSaveResult("tag", tagService.create(Staff, sId, tagInfo))
    			}
    			else { forbidden() }
    		}
    		else { notFound() }
    	}
    	else if (params.teamId) {
    		Long tId = params.long("teamId")
    		if (authService.exists(Team, tId)) {
    			if (authService.belongsToSameTeamAs(tId)) {
    				handleSaveResult("tag", tagService.create(Team, tId, tagInfo))
    			}
    			else { forbidden() }
    		}
    		else { notFound() }
    	}
    	else { badRequest() }
    }

    ////////////
    // Update //
    ////////////

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

    ////////////
    // Delete //
    ////////////

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

    /////////////
    // Helpers //
    /////////////
}
