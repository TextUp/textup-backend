package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.util.OptimisticLockingRetry
import static org.springframework.http.HttpStatus.*

@GrailsCompileStatic
@RestApi(name="Contact", description="Operations on contacts, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class ContactController extends BaseController {

    static namespace = "v1"

    // authService from superclass
    ContactService contactService

    // List
    // ----

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
        @RestApiParam(name="shareStatus", type="String", required=true,
            paramType=RestApiParamType.QUERY, description='''One of sharedByMe or sharedWithMe.
            This takes precedence over the status[] parameter. Only used with a staffId.'''),
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
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (Helpers.exactly(2, ["teamId", "tagId"], params)) {
            badRequest()
        }
        else if (params.teamId) {
            listForTeam(params)
        }
        else if (params.tagId) {
            listForTag(params)
        }
        else { listForStaff(params) }
    }
    protected def listForTeam(GrailsParameterMap params) {
        Team t1 = Team.get(params.long("teamId"))
        if (!t1 || !t1.phone) {
            return notFound()
        }
        else if (!authService.hasPermissionsForTeam(t1.id)) {
            return forbidden()
        }
        listForPhone(t1.phone, params)
    }
    protected def listForTag(GrailsParameterMap params) {
        ContactTag ct1 = ContactTag.get(params.long("tagId"))
        if (!ct1) {
            return notFound()
        }
        else if (!authService.hasPermissionsForTag(ct1.id)) {
            return forbidden()
        }
        genericListActionAllResults(Contact, ct1.getMembersByStatus(params.list("status[]")))
    }
    protected def listForStaff(GrailsParameterMap params) {
        Staff s1 = authService.loggedInAndActive
        if (!s1) {
            return forbidden()
        }
        else if (!s1.phone) {
            return notFound()
        }
        listForPhone(s1.phone, params)
    }
    protected def listForPhone(Phone p1, GrailsParameterMap params) {
        Closure count, list
        if (params.search) {
            count = { Map ps -> p1.countContacts(ps.search as String) }
            list = { Map ps -> p1.getContacts(ps.search as String) }
        }
        else if (params.shareStatus == "sharedByMe") { // returns CONTACTS
            count = { Map ps -> p1.countSharedByMe() }
            list = { Map ps -> p1.getSharedByMe(ps) }
        }
        else if (params.shareStatus == "sharedWithMe") { // returns SHARED CONTACTS
            count = { Map ps -> p1.countSharedWithMe() }
            list = { Map ps -> p1.getSharedWithMe(ps) }
        }
        else {
            params.statuses = params.list("status[]")
            count = { Map ps -> p1.countContacts(ps) }
            list = { Map ps -> p1.getContacts(ps) }
        }
        genericListActionForClosures(Contact, count, list, params)
    }

    // Show
    // ----

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
        // id will always be for the contact to avoid collisions, but we might
        // need to find the corresponding shared contact if the contact does
        // not belong to the staff's personal TextUp phone
        Long id = params.long("id")
        if (Contact.exists(id)) {
            if (authService.hasPermissionsForContact(id)) {
                genericShowAction(Contact, id)
            }
            else {
                Long scId = authService.getSharedContactIdForContact(id)
                if (scId) {
                    genericShowAction(SharedContact, scId)
                }
                else { forbidden() }
            }
        }
        else { notFound() }
    }

    // Save
    // ----

    @RestApiMethod(description="Create a new contact for staff member or team")
    @RestApiParams(params=[
        @RestApiParam(name="teamId", type="Number",
        	paramType=RestApiParamType.QUERY, description="Id of the team member")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="422", description="The updated fields created an invalid contact."),
        @RestApiError(code="403", description="You do not permissions to create \
            a new contact for this team."),
        @RestApiError(code="404",  description="The team member to \
            add this contact to was not found.")
    ])
    def save() {
    	if (!validateJsonRequest(request, "contact")) { return }
        Map contactInfo = (request.properties.JSON as Map).contact as Map
        if (params.teamId) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.hasPermissionsForTeam(tId)) {
                    handleSaveResult(Contact,
                        contactService.createForTeam(tId, contactInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else {
            handleSaveResult(Contact, contactService.createForStaff(contactInfo))
        }
    }

    // Update
    // ------

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
    @OptimisticLockingRetry
    def update() {
    	if (!validateJsonRequest(request, "contact")) { return; }
        Long id = params.long("id")
        if (authService.exists(Contact, id)) {
            if (authService.hasPermissionsForContact(id) ||
                authService.getSharedContactIdForContact(id)) {
                Map cInfo = (request.properties.JSON as Map).contact as Map
                handleUpdateResult(Contact, contactService.update(id, cInfo))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Delete
    // ------

    def delete() { notAllowed() }
}
