package org.textup.rest

import grails.converters.JSON
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*
import org.restapidoc.pojo.*
import grails.transaction.Transactional

@RestApi(name="Staff", description = "Operations on staff members after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class StaffController extends BaseController {

    static namespace = "v1"

    //authService from superclass
    def staffService

    //////////
    // List //
    //////////

    @RestApiMethod(description="List staff", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", paramType=RestApiParamType.QUERY, 
            required=false, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", paramType=RestApiParamType.QUERY, 
            required=false, description="Offset of results"),
        @RestApiParam(name="status[]", type="list", paramType=RestApiParamType.QUERY, 
            allowedvalues=["blocked", "pending", "staff", "admin"],
            required=false, description="List of staff statuses to restrict to"),
        @RestApiParam(name="organizationId", type="Number", paramType=RestApiParamType.QUERY, 
            required=true, description="Id of the organization to restrict results to"),
        @RestApiParam(name="teamId", type="Number", paramType=RestApiParamType.QUERY, 
            required=true, description="Id of the team to restrict results to")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The organization or team was not found."),
        @RestApiError(code="400", description="You must specify either organization id or team id but not both."),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() { 
        if (params.organizationId && params.teamId) { badRequest() }
        else if (params.organizationId) {
            Organization org = Organization.get(params.long("organizationId"))
            if (!org) { notFound(); return; }
            if (authService.isAdminAt(org.id)) {
                genericListActionForCriteria(Staff, Staff.forOrgAndStatuses(org, 
                    params.list("status[]")), params)
            }
            else { forbidden() }
        }
        else if (params.teamId) {
            Team t1 = Team.get(params.long("teamId"))
            if (!t1) { notFound(); return; }
            if (authService.isAdminForTeam(t1.id) || authService.belongsToSameTeamAs(t1.id)) {
                genericListActionForCriteria(Staff, Staff.membersForTeam(t1, 
                    params.list("status[]")), params)
            }
            else { forbidden() }
        }
        else { badRequest() }
    }

    //////////
    // Show //
    //////////

    @RestApiMethod(description="Show specifics about a staff member")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH, 
            description="Id of the staff member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The staff member was not found."),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def show() {
        Long id = params.long("id")
        if (Staff.exists(id)) {
            if (authService.hasPermissionsForStaff(id)) { genericShowAction(Staff, id) }
            else { forbidden() }
        }
        else { notFound() }
    }

    //////////
    // Save //
    //////////

    @RestApiMethod(description='''Create a new staff member and associate with 
        either a new or existing organization''')
    @RestApiResponseObject(objectIdentifier = "Staff")
    @RestApiBodyObject(name = "Staff")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="422",
            description="The updated fields created an invalid staff member.")
    ])
    def save() {
        // println "request.JSON: ${request.JSON}"

        if (!validateJsonRequest(request, "staff")) { return; }
        handleSaveResult(Staff, staffService.create(request.JSON.staff))
    }

    ////////////
    // Update //
    ////////////

    @RestApiMethod(description="Update an existing staff member")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH, 
            description="Id of the staff member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The staff member was not found."),
        @RestApiError(code="403", description='''The logged in staff member is trying 
            to modify another staff member and is not an admin at the organization.'''),
        @RestApiError(code="422", description="The updated fields created an invalid staff member.")
    ])
    def update() {
        if (!validateJsonRequest(request, "staff")) { return; }
        Long id = params.long("id")
        if (authService.exists(Organization, id)) {
            if (authService.isLoggedIn(id) || authService.isAdminAtSameOrgAs(id)) {
                handleUpdateResult(Staff, staffService.update(id, request.JSON.staff))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    ////////////
    // Delete //
    ////////////

    def delete() { notAllowed() }
}
