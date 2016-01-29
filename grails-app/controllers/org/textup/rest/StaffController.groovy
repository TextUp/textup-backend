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

    // authService from superclass
    def staffService

    // List
    // ----

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
            required=true, description="Id of the team to restrict results to"),
        @RestApiParam(name="canShareStaffId", type="Number", paramType=RestApiParamType.QUERY,
            required=true, description='''Restrict results to staff who are on the same
            team as the provided staff id'''),
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key to convert times to,
            include schedule intervals, defaults to UTC if unspecified or invalid''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The organization or team was not found."),
        @RestApiError(code="400", description='''You must specify either organization id
            or team id but not both'''),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (params.timezone) {
            request.timezone = params.timezone
        }
        if (!Helpers.exactly(1, ["organizationId", "teamId", "canShareStaffId"], params)) {
            badRequest()
        }
        else if (params.organizationId) {
            listForOrg(params)
        }
        else if (params.teamId) {
            listForTeam(params)
        }
        else { listForShareStaff(params) }
    }
    protected def listForOrg(GrailsParameterMap params) {
        Organization org = Organization.get(params.long("organizationId"))
        if (!org) {
            return notFound()
        }
        else if (!authService.isAdminAt(org.id)) {
            return forbidden()
        }
        params.statuses = params.list("status[]")
        genericListActionForClosures(Staff, { Map params ->
            org.countPeople(params)
        }, { Map params ->
            org.getPeople(params)
        }, params)
    }
    protected def listForTeam(GrailsParameterMap params) {
        Team t1 = Team.get(params.long("teamId"))
        if (!t1) {
            return notFound()
        }
        else if (!authService.hasPermissionsForTeam(t1.id)) {
            return forbidden()
        }
        genericListActionAllResults(Staff,
            t1.getMembers(params.list("status[]")))
    }
    protected def listForShareStaff(GrailsParameterMap params) {
        Long sId = params.long("canShareStaffId")
        if (!authService.isLoggedInAndActive(sId) &&
            !authService.isAdminAtSameOrgAs(sId)) {
            return forbidden()
        }
        genericListActionAllResults(Staff,
            s1.getCanShareWith(params.list("status[]")))
    }

    // Show
    // ----

    @RestApiMethod(description="Show specifics about a staff member")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the staff member"),
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key to convert times to,
            include schedule intervals, defaults to UTC if unspecified or invalid''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The staff member was not found."),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def show() {
        if (params.timezone) {
            request.timezone = params.timezone //for the json marshaller
        }
        Long id = params.long("id")
        if (Staff.exists(id)) {
            if (authService.hasPermissionsForStaff(id)) {
                genericShowAction(Staff, id)
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

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
        if (!validateJsonRequest(request, "staff")) { return; }
        handleSaveResult(Staff, staffService.create(request.JSON.staff))
    }

    // Update
    // ------

    @RestApiMethod(description="Update an existing staff member")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the staff member"),
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key that the dates, including schedule intervals
            passed in are in, defaults to UTC if unspecified or invalid''')
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
        if (params.timezone) {
            request.timezone = params.timezone //for the json marshaller
        }
        Long id = params.long("id")
        if (authService.exists(Staff, id)) {
            if (authService.isLoggedIn(id) ||
                authService.isAdminAtSameOrgAs(id)) {
                handleUpdateResult(Staff, staffService.update(id,
                    request.JSON.staff, params.timezone))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    def delete() { notAllowed() }
}
