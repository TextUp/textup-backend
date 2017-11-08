package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.util.OptimisticLockingRetry

@GrailsCompileStatic
@RestApi(name="Record",description = "Operations on records of communications \
    belonging to staff and teams, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class RecordController extends BaseController {

    static String namespace = "v1"

    AuthService authService
    RecordService recordService

    @Override
    protected String getNamespaceAsString() { namespace }

    // List
    // ----

    @RestApiMethod(description="List record items for a specific contact or tag", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="since", type="DateTime", required=false,
            paramType=RestApiParamType.QUERY, description="Get all record items \
                since. We assume is UTC."),
        @RestApiParam(name="before", type="DateTime", required=false,
            paramType=RestApiParamType.QUERY, description="Get all record items \
                before. We assume is UTC."),
        @RestApiParam(name="types[]", type="List", required=false,
            allowedvalues=["text", "call", "note"], paramType=RestApiParamType.QUERY,
            description="Filters listed items by type. Unspecified shows everything."),
        @RestApiParam(name="contactId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of associated contact"),
        @RestApiParam(name="tagId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of associated tag")
    ])
    @RestApiResponseObject(objectIdentifier = "RecordCall or RecordText")
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The specific contact or tag was not found."),
        @RestApiError(code="400", description='''You must specify either a contact id or \
            a tag id but not both.'''),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (!Helpers.exactly(1, ["contactId", "tagId"], params)) {
            badRequest()
        }
        else if (params.long("contactId")) {
            Long cId = params.long("contactId")
            Contact c1 = Contact.get(cId)
            if (!c1) {
                return notFound()
            }
            Record rec1
            if (authService.hasPermissionsForContact(cId)) {
                rec1 = c1.record
            }
            else {
                Long scId = authService.getSharedContactIdForContact(c1.id)
                if (scId) {
                    SharedContact sc1 = SharedContact.get(scId)
                    // only need canView permissions to view the items in this contact's record
                    if (sc1.canView) {
                        // WORKAROUND: bypass the canModify check in getRecord for the reasons listed
                        // in the longer comment below
                        rec1 = sc1.contact.record
                    }
                    else { return forbidden() }
                }
                else { return forbidden() }
            }
            // TODO: we need to come up with a better way to manage permission controls, perhaps
            // via wrapping the Record object in another object that manages access. This way,
            // we don't have to proxy the Record's methods on the SharedContact and other classes
            // Also, we don't have to artifically gate access to the Record object on the
            // SharedContact. The way that we currently implement it, direct access to the Record
            // object is only possible if the SharedContact has DELEGATE permissions. However, here
            // we use the Record object to fetch items, so we need to be able to access the Record
            // object in some limited way even for SharedContacts only with VIEW permissions
            listForRecord(rec1, params)
        }
        else { // tag id
            Long ctId = params.long("tagId")
            ContactTag ct1 = ContactTag.get(ctId)
            if (!ct1) {
                return notFound()
            }
            if (!authService.hasPermissionsForTag(ctId)) {
                return forbidden()
            }
            listForRecord(ct1.record, params)
        }
    }
    protected void listForRecord(Record rec1, GrailsParameterMap params) {
        Closure<Integer> count
        Closure<List<RecordItem>> list
        Collection<Class<? extends RecordItem>> types = recordService.parseTypes(params.list("types[]"))
        if (params.since && !params.before) {
            DateTime since = Helpers.toUTCDateTime(params.since)
            count = { rec1.countSince(since, types) }
            list = { Map options -> rec1.getSince(since, types, options) }
        }
        else if (params.since && params.before) {
            DateTime start = Helpers.toUTCDateTime(params.since),
                end = Helpers.toUTCDateTime(params.before)
            count = { rec1.countBetween(start, end, types) }
            list = { Map options -> rec1.getBetween(start, end, types, options) }
        }
        else {
            count = { rec1.countItems(types) }
            list = { Map options -> rec1.getItems(types, options) }
        }
        respondWithMany(RecordItem, count, list, params)
    }

    // Show
    // ----

    @RestApiMethod(description="Show specifics about a record item")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the record item")
    ])
    @RestApiResponseObject(objectIdentifier = "RecordCall or RecordText")
    @RestApiErrors(apierrors=[
        @RestApiError(code="403", description="You do not have permissions to \
            view this record item."),
        @RestApiError(code="404", description="The requested record item was \
            not found.")
    ])
    @Transactional(readOnly=true)
    def show() {
        RecordItem item1 = RecordItem.get(params.long("id"))
        if (item1) {
            if (authService.hasPermissionsForItem(item1.id)) {
                respond(item1, [status:ResultStatus.OK.apiStatus])
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    @RestApiMethod(description="Create a new record item")
    @RestApiParams(params=[
        @RestApiParam(name="teamId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the team to \
                create record item for")
    ])
    @RestApiBodyObject(name = "RecordCall or RecordText")
    @RestApiResponseObject(objectIdentifier = "List of RecordCall or RecordText")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request. Or, \
            you did not specify either staff id or team id (but not both)"),
        @RestApiError(code="403", description="You do not have permission to \
            add items to this record."),
        @RestApiError(code="422", description="The fields created an invalid \
            record item.")
    ])
    @OptimisticLockingRetry
    def save() {
        if (!validateJsonRequest(RecordItem, request)) { return }
        Map rInfo = (request.properties.JSON as Map).record as Map
        Long tId = params.long("teamId")
        if (tId) {
            respondWithResult(RecordItem, recordService.createForTeam(tId, rInfo))
        }
        else { respondWithResult(RecordItem, recordService.createForStaff(rInfo)) }
    }

    // Update
    // ------

    @RestApiMethod(description="Update an existing note")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
            paramType=RestApiParamType.PATH, description="Id of the note")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="405", description="The id provided is not a note \
            and cannot be updated"),
        @RestApiError(code="403", description="You do not have permission to modify this note."),
        @RestApiError(code="422", description="The updated fields created an invalid note.")
    ])
    def update() {
        if (!validateJsonRequest(RecordItem, request)) { return }
        Long id = params.long("id")
        Map noteInfo = (request.properties.JSON as Map).record as Map
        if (authService.exists(RecordNote, id)) {
            if (authService.hasPermissionsForItem(id)) {
                respondWithResult(RecordItem, recordService.update(id, noteInfo))
            }
            else { forbidden() }
        }
        else { notAllowed() }
    }

    // Delete
    // ------

    @RestApiMethod(description="Delete an existing note")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the note")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="403", description="You do not have permission to delete this note."),
        @RestApiError(code="405", description="The id provided is not a note \
            and cannot be deleted")
    ])
    def delete() {
        Long id = params.long("id")
        if (authService.exists(RecordNote, id)) {
            if (authService.hasPermissionsForItem(id)) {
                respondWithResult(Void, recordService.delete(id))
            }
            else { forbidden() }
        }
        else { notAllowed() }
    }
}
