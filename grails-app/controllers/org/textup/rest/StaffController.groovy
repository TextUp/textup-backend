package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@RestApi(name="Staff", description = "Operations on staff members after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class StaffController extends BaseController {

    static String namespace = "v1"

    AuthService authService
    ResultFactory resultFactory
    StaffService staffService

    @Override
    protected String getNamespaceAsString() { namespace }

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
            include schedule intervals, defaults to UTC if unspecified or invalid'''),
        @RestApiParam(name="search", type="String", required=false,
            paramType=RestApiParamType.QUERY, description='''String to search for in staff
            name, username or email. Limited to staff in the logged-in user\'s organization.''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The organization or team was not found."),
        @RestApiError(code="400", description='''You must specify either organization id
            or team id but not both'''),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (params.timezone) { //for the json marshaller
            request.setAttribute(Constants.REQUEST_TIMEZONE, params.timezone as String)
        }
        if (params.search) {
            listSearch(params)
        }
        else if (MapUtils.countKeys(["organizationId", "teamId", "canShareStaffId"], params) == 1) {
            if (params.organizationId) {
                listForOrg(params)
            }
            else if (params.teamId) {
                listForTeam(params)
            }
            else { listForShareStaff(params) }
        }
        else { badRequest() }
    }
    protected def listSearch(GrailsParameterMap params) {
        if (!authService.isActive) {
            return forbidden()
        }
        Staff loggedInStaff = authService.loggedInAndActive
        Organization org = loggedInStaff.org
        Closure<Integer> count = { Map ps -> org.countStaff(ps.search as String) }
        Closure<List<Staff>> list = { Map ps -> org.getStaff(ps.search as String, ps) }
        respondWithMany(Staff, count, list, params)
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
        Closure<Integer> count = { Map ps -> org.countPeople(ps) }
        Closure<List<Staff>> list = { Map ps -> org.getPeople(ps) }
        respondWithMany(Staff, count, list, params)
    }
    protected def listForTeam(GrailsParameterMap params) {
        Team t1 = Team.get(params.long("teamId"))
        if (!t1) {
            return notFound()
        }
        else if (!authService.hasPermissionsForTeam(t1.id)) {
            return forbidden()
        }
        Collection<Staff> staffList = t1.getMembersByStatus(params.list("status[]"))
        Closure<Integer> count = { staffList.size() }
        Closure<Collection<Staff>> list = { staffList }
        respondWithMany(Staff, count, list, params)
    }
    protected def listForShareStaff(GrailsParameterMap params) {
        Long sId = params.long("canShareStaffId")
        if (!authService.isLoggedInAndActive(sId) &&
            !authService.isAdminAtSameOrgAs(sId)) {
            return forbidden()
        }
        Staff s1 = Staff.get(sId)
        Collection<Staff> staffList = s1.getCanShareWith(params.list("status[]"))
        Closure<Integer> count = { staffList.size() }
        Closure<Collection<Staff>> list = { staffList }
        respondWithMany(Staff, count, list, params)
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
        if (params.timezone) { //for the json marshaller
            request.setAttribute(Constants.REQUEST_TIMEZONE, params.timezone as String)
        }
        Staff s1 = Staff.get(params.long("id"))
        if (s1) {
            if (authService.hasPermissionsForStaff(s1.id)) {
                respond(s1, [status:ResultStatus.OK.apiStatus])
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
    @RestApiParams(params=[
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key that the dates, including schedule intervals
            passed in are in, defaults to UTC if unspecified or invalid''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="422",
            description="The updated fields created an invalid staff member.")
    ])
    def save() {
        Map sInfo = getJsonPayload(Staff, request)
        if (sInfo == null) { return }
        String tz = params.timezone as String
        if (tz) { //for the json marshaller
            request.setAttribute(Constants.REQUEST_TIMEZONE, tz)
        }
        respondWithResult(Staff, staffService.create(sInfo, tz))
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
        Map sInfo = getJsonPayload(Staff, request)
        if (sInfo == null) { return }
        String tz = params.timezone as String
        if (params.timezone) { //for the json marshaller
            request.setAttribute(Constants.REQUEST_TIMEZONE, tz)
        }
        Long id = params.long("id")
        if (authService.exists(Staff, id)) {
            if (authService.isLoggedIn(id) || authService.isAdminAtSameOrgAs(id)) {
                respondWithResult(Staff, staffService.update(id, sInfo, tz))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    def delete() { notAllowed() }
}
