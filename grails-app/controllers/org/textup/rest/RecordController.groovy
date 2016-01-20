package org.textup.rest

import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.hibernate.StaleObjectStateException
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.orm.hibernate4.HibernateOptimisticLockingFailureException
import org.springframework.security.access.annotation.Secured
import org.textup.*
import static org.springframework.http.HttpStatus.*

@RestApi(name="Record", description = "Operations on records of communications belonging to staff and teams, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class RecordController extends BaseController {

    static namespace = "v1"

    //authService from superclass
    def recordService

    //////////
    // List //
    //////////

    @RestApiMethod(description="List contacts for a specific staff or team", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="since", type="DateTime", required=false,
            paramType=RestApiParamType.QUERY, description="Get all record items since. We assume is UTC."),
        @RestApiParam(name="before", type="DateTime", required=false,
            paramType=RestApiParamType.QUERY, description="Get all record items before. We assume is UTC."),
        @RestApiParam(name="contactId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the associated contact")
    ])
    @RestApiResponseObject(objectIdentifier = "RecordCall, RecordText, or RecordNote")
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The specific contact was not found."),
        @RestApiError(code="400", description="You must specify a contact id."),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (params.contactId) {
            Long cId = params.long("contactId")
            Contact c1 = Contact.get(cId)
            if (c1) {
                Contactable cont
                if (authService.hasPermissionsForContact(c1.id)) { cont = c1 }
                else {
                    Long scId = authService.getSharedContactForContact(c1.id)
                    if (scId) { cont = SharedContact.get(scId) }
                    else { forbidden(); return; }
                }
                Closure count, list
                if (params.since && !params.before) {
                    DateTime since = Helpers.toUTCDateTime(params.since)
                    count = { Map params -> cont.countSince(since) }
                    list = { Map params -> cont.getSince(since, params) }
                }
                else if (params.since && params.before) {
                    DateTime start = Helpers.toUTCDateTime(params.since),
                        end = Helpers.toUTCDateTime(params.before)
                    count = { Map params -> cont.countBetween(start, end) }
                    list = { Map params -> cont.getBetween(start, end, params) }
                }
                else {
                    count = { Map params -> cont.countItems() }
                    list = { Map params -> cont.getItems(params) }
                }
                genericListActionForClosures("record", count, list, params)
            }
            else { notFound() }
        }
        else { badRequest() }
    }

    //////////
    // Show //
    //////////

    @RestApiMethod(description="Show specifics about a record item")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the record item")
    ])
    @RestApiResponseObject(objectIdentifier = "RecordCall, RecordText, or RecordNote")
    @RestApiErrors(apierrors=[
        @RestApiError(code="403", description="You do not have permissions to view this record item."),
        @RestApiError(code="404", description="The requested record item was not found.")
    ])
    @Transactional(readOnly=true)
    def show() {
        Long id = params.long("id")
        if (RecordItem.exists(id)) {
            if (authService.hasPermissionsForItem(id)) { genericShowAction(RecordItem, id) }
            else { forbidden() }
        }
        else { notFound() }
    }

    //////////
    // Save //
    //////////

    @RestApiMethod(description="Create a new record item")
    @RestApiParams(params=[
        @RestApiParam(name="staffId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the staff to create record item for"),
        @RestApiParam(name="teamId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the team to create record item for")
    ])
    @RestApiBodyObject(name = "RecordCall, RecordText, or RecordNote")
    @RestApiResponseObject(objectIdentifier = "List of RecordCall, RecordText, or RecordNote")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request. Or, you did not specify either staff id or team id (but not both)"),
        @RestApiError(code="403", description="You do not have permission to add items to this record."),
        @RestApiError(code="422", description="The fields created an invalid record item.")
    ])
    def save() {
        if (!validateJsonRequest(request, "record")) { return; }
        Map recordInfo = request.JSON.record
        if (params.staffId && params.teamId) { badRequest() }
        else if (params.staffId) {
            Long sId = params.long("staffId")
            if (authService.exists(Staff, sId)) {
                if (authService.isLoggedIn(sId)) {
                    try {
                        handleSaveRecordResult(recordService.create(Staff, sId, recordInfo))
                    }
                    catch (StaleObjectStateException | HibernateOptimisticLockingFailureException e) {
                        handleSaveRecordResult(recordService.create(Staff, sId, recordInfo))
                    }
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else if (params.teamId) {
            Long tId = params.long("teamId")
            if (authService.exists(Team, tId)) {
                if (authService.belongsToSameTeamAs(tId)) {
                    handleSaveRecordResult(recordService.create(Team, tId, recordInfo))
                }
                else { forbidden() }
            }
            else { notFound() }
        }
        else { badRequest() }
    }
    protected def handleSaveRecordResult(Result<RecordResult> res) {
        if (res.success) {
            RecordResult recRes = res.payload
            Map meta = [:]
            if (recRes.invalidOrForbiddenContactableIds) {
                meta.failedContactableIds = recRes.invalidOrForbiddenContactableIds
            }
            if (recRes.invalidOrForbiddenTagIds) {
                meta.failedTagIds = recRes.invalidOrForbiddenTagIds
            }
            if (recRes.invalidNumbers) {
                meta.invalidNumbers = recRes.invalidNumbers
            }
            withFormat {
                json {
                    respond recRes.newItems, [status:CREATED, meta:meta]
                }
            }
        }
        else { handleResultFailure(res) }
    }

    ////////////
    // Update //
    ////////////

    @RestApiMethod(description="Update an existing record item")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the record item")
    ])
    @RestApiBodyObject(name = "RecordCall, RecordText, or RecordNote")
    @RestApiResponseObject(objectIdentifier = "RecordCall, RecordText, or RecordNote")
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The requested record item was not found."),
        @RestApiError(code="403", description="This record item is not editable."),
        @RestApiError(code="422", description="The updated fields created an invalid record item.")
    ])
    def update() {
        if (!validateJsonRequest(request, "record")) { return; }
        Long id = params.long("id")
        if (authService.exists(RecordItem, id)) {
            if (authService.hasPermissionsForItem(id)) {
                handleUpdateResult("record", recordService.update(id, request.JSON.record))
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
