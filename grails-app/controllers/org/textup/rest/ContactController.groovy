package org.textup.rest

import grails.converters.JSON
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

@RestApi(name="Contact", description="Operations on contacts, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class ContactController extends BaseController {

    static namespace = "v1"

    def contactService

    //////////
    // List //
    //////////

    @RestApiMethod(description="List contacts for a specific staff or team", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
        	paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
        	paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="status[]", type="List", paramType=RestApiParamType.QUERY,
            allowedvalues=["unread", "active", "archived", "blocked"],
            required=false, description='''List of staff statuses to restrict to.
            Default showing unread and active. The two shared stauses are only valid when
            specifying a staffId and will result in a 400 error otherwise.'''),
        @RestApiParam(name="staffStatus", type="String", required=true,
            paramType=RestApiParamType.QUERY, description='''One of sharedByMe or sharedWithMe.
            This takes precedence over the status[] parameter. Only used with a staffId.'''),
        @RestApiParam(name="staffId", type="Number", required=true,
        	paramType=RestApiParamType.QUERY, description="Id of the staff member"),
        @RestApiParam(name="teamId", type="Number", required=true,
        	paramType=RestApiParamType.QUERY, description="Id of the team member"),
        @RestApiParam(name="tagId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the team member"),
        @RestApiParam(name="search", type="String", required=false,
            paramType=RestApiParamType.QUERY, description='''String to search for in contact name,
            limited to active and unread contacts''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description='''The staff or team was not found. Or, the
            staff or team specified is not allowed to have contacts.'''),
        @RestApiError(code="400", description='''You must specify either a staff id or
            team id or tag id, but not more than one. Or you specified an invalid staffStatus.'''),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        //need to account for both sharedcontact and contact
        //set tagId if in context of a tag
        if (moreThanOneId(params)) { badRequest() }
        else if (params.staffId) {
            Staff s1 = Staff.get(params.long("staffId"))
            if (!s1 || !s1.phone) { notFound() }
            else if (authService.isLoggedIn(s1.id)) {
                if (params.search) {
                    String query = params.search
                    if (!query.startsWith("%")) query = "%$query"
                    if (!query.endsWith("%")) query = "$query%"
                    genericListActionForCriteria(Contact,
                        Contact.iLikeForNameAndPhone(query, s1.phone), params)
                }
                else if (params.staffStatus) {
                    if (params.staffStatus == "sharedByMe") {
                        Closure count = { params ->
                                s1.phone.countSharedByMe()
                            }, list  = { params ->
                                List<SharedContact> scList = s1.phone.getSharedByMe(params)
                                scList ? scList : [] //return sharedContact, will be handled in marshaller
                            }
                        genericListActionForClosures(Contact, count, list, params)
                    }
                    else if (params.staffStatus == "sharedWithMe") {
                        Closure count = { params ->
                                s1.phone.countSharedWithMe()
                            }, list = { params ->
                                List<SharedContact> scList = s1.phone.getSharedWithMe(params)
                                scList ? scList : [] //return sharedContact, will be handled in marshaller
                            }
                        genericListActionForClosures(Contact, count, list, params)
                    }
                    else {
                        respondWithError(g.message(code:"contactController.index.invalidStaffStatus",
                            args:[params.staffStatus]), BAD_REQUEST)
                    }
                }
                else {
                    params.status = params.list("status[]")
                    Closure count = { Map params -> s1.phone.countContacts(params) },
                        list = { Map params -> s1.phone.getContacts(params) }
                    genericListActionForClosures(Contact, count, list, params)
                }
            }
            else { forbidden() }
        }
        else if (params.teamId) {
            Team t1 = Team.get(params.long("teamId"))
            if (!t1 || !t1.phone) { notFound() }
            else if (authService.belongsToSameTeamAs(t1.id)) {
                if (params.search) {
                    String query = params.search
                    if (!query.startsWith("%")) query = "%$query"
                    if (!query.endsWith("%")) query = "$query%"
                    genericListActionForCriteria(Contact,
                        Contact.iLikeForNameAndPhone(query, s1.phone), params)
                }
                else {
                    genericListActionForCriteria(Contact, Contact.forPhoneAndStatuses(t1.phone,
                        params.list("status[]")), params)
                }
            }
            else { forbidden() }
        }
        else if (params.tagId) {
            ContactTag ct1 = ContactTag.get(params.long("tagId"))
            if (!ct1) { notFound() }
            else if (authService.hasPermissionsForTag(ct1.id)) {
                request.tagId = ct1.id //for the json marshaller
                genericListActionForCriteria(Contact, Contact.forTagAndStatuses(ct1,
                    params.list("status[]")), params)
            }
            else { forbidden() }
        }
        else { badRequest() }
    }
    protected boolean moreThanOneId(GrailsParameterMap params) {
        (params.staffId && params.teamId) ||
        (params.staffId && params.tagId) ||
        (params.tagId && params.teamId) ||
        (params.tagId && params.staffId && params.teamId)
    }

    //////////
    // Show //
    //////////

    @RestApiMethod(description="Show specifics about a contact")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
        	paramType=RestApiParamType.PATH, description="Id of the contact")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="403", description="You do not have permissions to view this contact."),
        @RestApiError(code="404",  description="The requested contact was not found.")
    ])
    @Transactional(readOnly=true)
    def show() {
        //id will always be for the contact to avoid collisions, but we might need to
        //find the corresponding shared contact if we don't have permissions for the contact
        Long id = params.long("id")
        if (Contact.exists(id)) {
            if (authService.hasPermissionsForContact(id)) { genericShowAction(Contact, id) }
            else {
                Long scId = authService.getSharedContactForContact(id)
                if (scId) { genericShowAction(SharedContact, scId) }
                else { forbidden() }
            }
        }
        else { notFound() }
    }

    //////////
    // Save //
    //////////

    @RestApiMethod(description="Create a new contact for staff member or team")
    @RestApiParams(params=[
        @RestApiParam(name="staffId", type="Number",
        	paramType=RestApiParamType.QUERY, description="Id of the staff member"),
        @RestApiParam(name="teamId", type="Number",
        	paramType=RestApiParamType.QUERY, description="Id of the team member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request. Or, you did not specify either staff id or team id (but not both)"),
        @RestApiError(code="422", description="The updated fields created an invalid contact."),
        @RestApiError(code="403", description="You do not permissions to create a new contact for this staff member or team."),
        @RestApiError(code="404",  description="The staff or team member to add this contact to was not found.")
    ])
    def save() {
    	if (!validateJsonRequest(request, "contact")) { return; }
        Map contactInfo = request.JSON.contact
        if (params.staffId && params.teamId) { badRequest() }
        else if (params.staffId) {
            Long sId = params.long("staffId")
            if (authService.exists(Staff, sId)) {
                if (authService.isLoggedIn(sId)) {
                    handleSaveResult(Contact, contactService.create(Staff, sId, contactInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else if (params.teamId) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.belongsToSameTeamAs(tId)) {
                    handleSaveResult(Contact, contactService.create(Team, tId, contactInfo))
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

    @RestApiMethod(description="Update an existing contact")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
        	paramType=RestApiParamType.PATH, description="Id of the contact")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The requested contact was not found."),
        @RestApiError(code="403", description="You do not have permission to modify this contact."),
        @RestApiError(code="422", description="The updated fields created an invalid contact.")
    ])
    def update() {
    	if (!validateJsonRequest(request, "contact")) { return; }
        Long id = params.long("id")
        if (authService.exists(Contact, id)) {
            if (authService.hasPermissionsForContact(id)) {
                handleUpdateResult(Contact, contactService.update(id, request.JSON.contact))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    ////////////
    // Delete //
    ////////////

    @RestApiMethod(description="Delete an existing contact")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="number", paramType=RestApiParamType.PATH,
        	description="Id of the contact")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404", description="The requested contact was not found."),
        @RestApiError(code="403", description="You do not have permission to modify this contact.")
    ])
    def delete() {
        Long id = params.long("id")
        if (authService.exists(Contact, id)) {
            if (authService.hasPermissionsForContact(id)) {
                handleDeleteResult(contactService.delete(id))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    /////////////
    // Helpers //
    /////////////
}
