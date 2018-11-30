package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.joda.time.format.*
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.util.OptimisticLockingRetry
import org.textup.validator.*

@GrailsTypeChecked
@RestApi(name="Record",description = "Operations on records of communications \
    belonging to staff and teams, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class RecordController extends BaseController {

    static String namespace = "v1"

    final String EXPORT_TYPE_SINGLE_STREAM = "singleStream"
    final String EXPORT_TYPE_GROUPED = "groupByEntity"

    AuthService authService
    PDFService pdfService
    RecordService recordService
    ResultFactory resultFactory

    @Override
    protected String getNamespaceAsString() { namespace }

    // List
    // ----

    @RestApiMethod(description="List record items for a specific contact or tag", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="format", type="String", required=false,
            allowedvalues=["json", "pdf"], paramType=RestApiParamType.QUERY,
            description="Specify what format to return results in"),
        @RestApiParam(name="max", type="Number", required=false,
            paramType=RestApiParamType.QUERY,
            description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY,
            description="Offset of results"),
        @RestApiParam(name="since", type="DateTime", required=false,
            paramType=RestApiParamType.QUERY, description="Get all record items \
                since. We assume is UTC."),
        @RestApiParam(name="before", type="DateTime", required=false,
            paramType=RestApiParamType.QUERY, description="Get all record items \
                before. We assume is UTC."),
        @RestApiParam(name="types[]", type="List", required=false,
            allowedvalues=["text", "call", "note"], paramType=RestApiParamType.QUERY,
            description="Filters listed items by type. Unspecified shows everything."),
        @RestApiParam(name="exportFormatType", type="String", required=false,
            allowedvalues=["singleStream", "groupByEntity"],
            paramType=RestApiParamType.QUERY,
            description="For non-json payloads, specifies how record items should be grouped, default is `singleStream`"),
        @RestApiParam(name="teamId", type="Number", required=false,
            paramType=RestApiParamType.QUERY,
            description="Id of the team that the passed-in contact, shared contact, and tag ids belong to"),
        @RestApiParam(name="contactIds[]", type="List", required=false,
            paramType=RestApiParamType.QUERY, description="Ids of contacts to fetch records for"),
        @RestApiParam(name="sharedContactIds[]", type="List", required=false,
            paramType=RestApiParamType.QUERY,
            description="Ids of contacts shared with the current user to fetch records for"),
        @RestApiParam(name="tagIds[]", type="List", required=false,
            paramType=RestApiParamType.QUERY, description="Ids of tags to fetch records for")
    ])
    @RestApiResponseObject(objectIdentifier = "RecordCall, RecordText, or RecordNote")
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="Could not find team with specified id."),
        @RestApiError(code="403", description="You do not have permission to fetch records from team with this id.")
    ])
    @Transactional(readOnly=true)
    def index() {
        // step 1: fetch appropriate phone
        Phone p1 = authService.loggedInAndActive?.phone
        Long tId = params.long("teamId")
        if (tId) {
            if (authService.exists(Team, tId)) {
                if (authService.hasPermissionsForTeam(tId)) {
                    p1 = Team.get(tId)?.phone
                }
                else { return forbidden() }
            }
            else { return notFound() }
        }
        // step 2: build record item request
        boolean isGrouped = (params.exportFormatType == EXPORT_TYPE_GROUPED)
        Result<RecordItemRequest> res = RecordUtils.buildRecordItemRequest(p1, params, isGrouped)
        if (!res.success) {
            return respondWithResult(null, res)
        }
        Utils.trySetOnRequest(Constants.REQUEST_PAGINATION_OPTIONS, params)
        // step 3: return data in specified format
        RecordItemRequest itemRequest = res.payload
        if (params.format == "pdf") {
            String timestamp = DateTimeUtils.fileTimestampFormat.print(DateTime.now())
            String exportFileName = "textup-export-${timestamp}.pdf"
            respondWithPDF(exportFileName, pdfService.buildRecordItems(itemRequest))
        }
        else {
            Closure<Integer> count = { itemRequest.countRecordItems() }
            Closure<List<? extends ReadOnlyRecordItem>> list = { Map options ->
                itemRequest.getRecordItems(options)
            }
            respondWithMany(RecordItem, count, list, params)
        }
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
        Map inputData = getJsonPayload(RecordItem, request)
        if (inputData == null) { return }
        TypeConvertingMap rInfo = new TypeConvertingMap(inputData)
        Long tId = params.long("teamId")
        if (tId && authService.exists(Team, tId)) {
            if (authService.hasPermissionsForTeam(tId)) {
                createForPhone(Team.get(tId)?.phone?.id, rInfo)
            }
            else { forbidden() }
        }
        else { createForPhone(authService.loggedInAndActive?.phone?.id, rInfo) }
    }
    protected void createForPhone(Long phoneId, TypeConvertingMap body) {
        Result<Class<RecordItem>> res = RecordUtils.determineClass(body)
        if (!res.success) {
            badRequest()
            return
        }
        if (validateCreateBody(res.payload, body)) {
            respondWithResult(RecordItem, recordService.create(phoneId, body))
        }
    }
    protected boolean validateCreateBody(Class<RecordItem> clazz, TypeConvertingMap body) {
        switch(clazz) {
            case RecordCall:
                if (!MapUtils.exactly(1, ["callContact", "callSharedContact"], body)) {
                    respondWithResult(RecordItem, resultFactory.failWithCodeAndStatus(
                        "recordController.create.tooManyForCall", ResultStatus.BAD_REQUEST))
                    return false
                }
                break
            case RecordNote:
                if (!MapUtils.exactly(1, ["forContact", "forSharedContact", "forTag"], body)) {
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
        Map inputData = getJsonPayload(RecordItem, request)
        if (inputData == null) { return }
        TypeConvertingMap noteInfo = new TypeConvertingMap(inputData)
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
