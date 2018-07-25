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
    ResultFactory resultFactory

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
            if (authService.hasPermissionsForContact(cId)) {
                listForRecord(c1.record, params)
            }
            else {
                Long scId = authService.getSharedContactIdForContact(c1.id)
                if (!scId) { return forbidden() }
                SharedContact sc1 = SharedContact.get(scId)
                if (!sc1) { return forbidden() }
                Result<ReadOnlyRecord> res = sc1.tryGetReadOnlyRecord()
                if (res.success) {
                    listForRecord(res.payload, params)
                }
                else { forbidden() }
            }
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
    protected void listForRecord(ReadOnlyRecord rec1, GrailsParameterMap params) {
        Closure<Integer> count
        Closure<List<ReadOnlyRecordItem>> list
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
        Map rInfo = getJsonPayload(RecordItem, request)
        if (rInfo == null) { return }
        Long tId = params.long("teamId")
        if (authService.exists(Team, tId)) {
            if (authService.hasPermissionsForTeam(tId)) {
                createForPhone(Team.get(tId)?.phone?.id, rInfo)
            }
            else { forbidden() }
        }
        else { createForPhone(authService.loggedInAndActive?.phone?.id, rInfo) }
    }
    protected void createForPhone(Long phoneId, Map body) {
        if (validateCreateBody(body)) {
            respondWithResult(RecordItem, recordService.create(phoneId, body))
        }
    }
    protected boolean validateCreateBody(Map body) {
        Result<Class<RecordItem>> res = recordService.determineClass(body)
        if (!res.success) {
            badRequest()
            return false
        }
        switch(res.payload) {
            case RecordCall:
                if (!Helpers.exactly(1, ["callContact", "callSharedContact"], body)) {
                    respondWithResult(RecordItem, resultFactory.failWithCodeAndStatus(
                        "recordController.create.tooManyForCall", ResultStatus.BAD_REQUEST))
                    return false
                }
                break
            case RecordNote:
                if (!Helpers.exactly(1, ["forContact", "forSharedContact", "forTag"], body)) {
                    respondWithResult(RecordItem, resultFactory.failWithCodeAndStatus(
                        "recordController.create.tooManyForNote", ResultStatus.BAD_REQUEST))
                    return false
                }
                break
        }
        return true
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
        Map noteInfo = getJsonPayload(RecordItem, request)
        if (noteInfo == null) { return }
        Long id = params.long("id")
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
