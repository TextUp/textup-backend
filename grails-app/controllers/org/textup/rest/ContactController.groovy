package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@RestApi(name="Contact", description="Operations on contacts, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class ContactController extends BaseController {

    static String namespace = "v1"

    AuthService authService
    ContactService contactService
    DuplicateService duplicateService

    @Override
    protected String getNamespaceAsString() { namespace }

    // List
    // ----

    @RestApiMethod(description="List contacts for a specific staff or team", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
        	paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="id[]", type="List", required=false,
            paramType=RestApiParamType.QUERY,
            description="Show only the contacts that match the list of ids"),
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
            paramType=RestApiParamType.QUERY, description="Id of the tag"),
        @RestApiParam(name="search", type="String", required=false,
            paramType=RestApiParamType.QUERY, description='''String to search for in contact name,
            limited to active and unread contacts'''),
        @RestApiParam(name="duplicates", type="Boolean", required=false,
            paramType=RestApiParamType.QUERY, description='''When true, will disregard all other
            query parameters except for the team or tag id parameters and will look through all
            contacts to try to find possible duplicates for contacts (not shared contacts)''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description='''The staff or team was not found. Or, the
            staff or team specified is not allowed to have contacts.'''),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (params.list("ids[]")) {
            listForIds(params)
        }
        else if (MapUtils.countKeys(["teamId", "tagId"], params) == 2) {
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
    protected def listForTeam(TypeConvertingMap params) {
        Team t1 = Team.get(params.long("teamId"))
        if (!t1 || !t1.phone) {
            return notFound()
        }
        else if (!authService.hasPermissionsForTeam(t1.id)) {
            return forbidden()
        }
        listForPhone(t1.phone, params)
    }
    protected def listForStaff(TypeConvertingMap params) {
        Staff s1 = authService.loggedInAndActive
        if (!s1) {
            return forbidden()
        }
        else if (!s1.phone) {
            return notFound()
        }
        listForPhone(s1.phone, params)
    }

    protected def listForIds(TypeConvertingMap params) {
        Collection<Long> ids = TypeConversionUtils.allTo(Long, TypeConversionUtils.to(Collection, params.list("ids[]"))),
            cIds = [],
            scIds = []
        ids.each { Long id ->
            if (!Contact.exists(id)) { return }
            if (authService.hasPermissionsForContact(id)) {
                cIds << id
            }
            else {
                Long scId = authService.getSharedContactIdForContact(id)
                if (scId) {
                    scIds << scId
                }
            }
        }
        List<? extends Contactable> results = []
        results.addAll(Contact.getAll(cIds as Iterable))
        results.addAll(SharedContact.getAll(scIds as Iterable))
        respondWithMany(Contactable, { results.size() }, { results })
    }
    protected def listForTag(TypeConvertingMap params) {
        ContactTag ct1 = ContactTag.get(params.long("tagId"))
        if (!ct1) {
            return notFound()
        }
        else if (!authService.hasPermissionsForTag(ct1.id)) {
            return forbidden()
        }
        Collection<Contact> contacts = ct1.getMembersByStatus(params.list("status[]"))
        if (params.boolean("duplicates")) {
            listForDuplicates(duplicateService.findDuplicates(contacts*.id), params)
        }
        else { respondWithMany(Contactable, { contacts.size() }, { contacts }) }
    }
    protected def listForPhone(Long phoneId, TypeConvertingMap params) {
        if (params.boolean("duplicates")) {
            return listForDuplicates(duplicateService.findAllDuplicates(p1.id), params)
        }
        Closure<Integer> count
        Closure<List<Contactable>> list
        if (params.shareStatus == "sharedByMe") {
            DetachedCriteria<SharedContact> query = SharedContact.forOptions(null, p1.id)
            count = { query.count() }
            list = { Map p -> query.list(p) }
        }
        else if (params.shareStatus == "sharedWithMe") {
            DetachedCriteria<SharedContact> query = SharedContact.forOptions(null, null, p1.id)
            count = { query.count() }
            list = { Map p -> query.list(p) }
        }
        else {
            List<ContactStatus> statuses = TypeConversionUtils.toEnumList(ContactStatus,
                params.list("status[]"), ContactStatus.VISIBLE_STATUSES)
            String searchVal = params.search
            DetachedCriteria<Contact> query = ContactableUtils
                .allForPhoneIdWithOptions(p1.id, searchVal, statuses)
            count = { query.count() }
            list = { Map p -> query.build(ContactableUtils.buildForSort()).list() }
        }
        respondWithMany(Contactable, count, { Map p -> ContactableUtils.normalize(list(p)) }, params)
    }
    protected def listForDuplicates(Result<List<MergeGroup>> res, TypeConvertingMap params) {
        if (!res.success) {
            return respondWithResult(Object, res)
        }
        List<MergeGroup> merges = res.payload
        Closure<Integer> count = { merges.size() }
        Closure<List<MergeGroup>> list = { merges }
        respondWithMany(MergeGroup, count, list, params)
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
        Contact c1 = Contact.get(id)
        if (c1) {
            if (authService.hasPermissionsForContact(id)) {
                respond(c1, [status:ResultStatus.OK.apiStatus])
            }
            else { showForSharedContact(id) }
        }
        else { notFound() }
    }
    protected def showForSharedContact(Long id) {
        Long scId = authService.getSharedContactIdForContact(id)
        if (scId) {
            SharedContact sc1 = SharedContact.get(scId)
            if (sc1) {
                respond(sc1, [status:ResultStatus.OK.apiStatus])
            }
            else { notFound() }
        }
        else { forbidden() }
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
        Map contactInfo = getJsonPayload(Contactable, request)
        if (contactInfo == null) { return }
        if (params.teamId) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.hasPermissionsForTeam(tId)) {
                    respondWithResult(Contactable,
                        contactService.create(tId, PhoneOwnershipType.GROUP, contactInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else {
            Long loggedInId = authService.loggedInAndActive?.id
            respondWithResult(Contactable,
                contactService.create(loggedInId, PhoneOwnershipType.INDIVIDUAL, contactInfo))
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
        Map contactInfo = getJsonPayload(Contactable, request)
        if (contactInfo == null) { return }
        Long id = params.long("id")
        if (authService.exists(Contact, id)) {
            Long scId = authService.getSharedContactIdForContact(id)
            if (scId || authService.hasPermissionsForContact(id)) {
                respondWithResult(Contactable, contactService.update(id, contactInfo, scId))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Delete
    // ------

    @RestApiMethod(description="Delete an existing contact")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the contact")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404", description="The requested contact was not found."),
        @RestApiError(code="403", description="You do not have permission to delete this contact.")
    ])
    def delete() {
        Long id = params.long("id")
        if (authService.exists(Contact, id)) {
            // only the only of the contact can delete it. Collaborators via sharing
            // are not permitted to delete contacts that have been shared with them
            if (authService.hasPermissionsForContact(id)) {
                respondWithResult(Void, contactService.delete(id))
            }
            else { forbidden() }
        }
        else { notFound() }
    }
}
