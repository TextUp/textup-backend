package org.textup.rest

import grails.compiler.GrailsCompileStatic
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

@GrailsCompileStatic
@RestApi(name="Record",description = "Operations on records of communications \
    belonging to staff and teams, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class RecordController extends BaseController {

    static namespace = "v1"

    //authService from superclass
    RecordService recordService

    // List
    // ----

    @RestApiMethod(description="List contacts for a specific staff or team", listing=true)
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
        @RestApiParam(name="contactId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of associated contact")
    ])
    @RestApiResponseObject(objectIdentifier = "RecordCall or RecordText")
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The specific contact was not found."),
        @RestApiError(code="400", description="You must specify a contact id."),
        @RestApiError(code="403", description="You do not have permission to do this.")
    ])
    @Transactional(readOnly=true)
    def index() {
        if (params.long("contactId")) {
            Long cId = params.long("contactId")
            Contact c1 = Contact.get(cId)
            if (c1) {
                Contactable cont
                if (authService.hasPermissionsForContact(cId)) {
                    cont = c1
                }
                else {
                    Long scId = authService.getSharedContactIdForContact(c1.id)
                    if (scId) {
                        cont = SharedContact.get(scId)
                    }
                    else { forbidden(); return; }
                }
                Closure count, list
                if (params.since && !params.before) {
                    DateTime since = Helpers.toUTCDateTime(params.since)
                    count = { Map params ->
                        (cont as Contactable).countSince(since)
                    }
                    list = { Map params ->
                        (cont as Contactable).getSince(since, params)
                    }
                }
                else if (params.since && params.before) {
                    DateTime start = Helpers.toUTCDateTime(params.since),
                        end = Helpers.toUTCDateTime(params.before)
                    count = { Map params ->
                        (cont as Contactable).countBetween(start, end)
                    }
                    list = { Map params ->
                        (cont as Contactable).getBetween(start, end, params)
                    }
                }
                else {
                    count = { Map params ->
                        (cont as Contactable).countItems()
                    }
                    list = { Map params ->
                        (cont as Contactable).getItems(params)
                    }
                }
                genericListActionForClosures("record", count, list, params)
            }
            else { notFound() }
        }
        else { badRequest() }
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
        Long id = params.long("id")
        if (RecordItem.exists(id)) {
            if (authService.hasPermissionsForItem(id)) {
                genericShowAction(RecordItem, id)
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
    def save() {
        if (!validateJsonRequest(request, "record")) { return; }
        Map rInfo = (request.properties.JSON as Map).record as Map
        try {
            if (params.long("teamId")) {
                Long tId = params.long("teamId")
                handleResultListForSave("record", recordService.createForTeam(tId, rInfo))
            }
            else {
                handleResultListForSave("record", recordService.createForStaff(rInfo))
            }
        }
        catch (StaleObjectStateException |
            HibernateOptimisticLockingFailureException e) {
            log.debug("RecordController.save: concurrent exception: \
                e.message: e.class: ${e.class}, ${e.message}, e: $e")
            this.save()
        }
    }

    def update() { notAllowed() }
    def delete() { notAllowed() }
}
