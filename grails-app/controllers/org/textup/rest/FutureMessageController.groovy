package org.textup.rest

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.restapidoc.pojo.*
import org.springframework.security.access.annotation.Secured
import org.textup.*

@GrailsCompileStatic
@RestApi(name="FutureMessage",description = "Operations on messages (call or text) to be \
	send in the future for staff or teams, after logging in.")
@Secured(["ROLE_ADMIN", "ROLE_USER"])
class FutureMessageController extends BaseController {

	static String namespace = "v1"

    AuthService authService
	FutureMessageService futureMessageService

    @Override
    protected String getNamespaceAsString() { namespace }

	// List
	// ----

    @RestApiMethod(description="List future messages for a specific contact or tag", listing=true)
    @RestApiParams(params=[
        @RestApiParam(name="max", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Max number of results"),
        @RestApiParam(name="offset", type="Number", required=false,
            paramType=RestApiParamType.QUERY, description="Offset of results"),
        @RestApiParam(name="contactId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the contact to show messages for"),
        @RestApiParam(name="tagId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the tag to show messages for")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404",description="The contact or tag was not found."),
        @RestApiError(code="403", description="You do not have permission to do this."),
        @RestApiError(code="400", description="You must specify either contact id or tag id, but not both")
    ])
	@Transactional(readOnly=true)
    def index() {
    	if (!Helpers.exactly(1, ["contactId", "tagId"], params)) {
    		badRequest()
		}
    	else if (params.long("contactId")) {
            listForContact(params)
        }
        else { listForTag(params) } // tagId
    }
    protected def listForContact(GrailsParameterMap params) {
    	Long cId = params.long("contactId")
        Contact c1 = Contact.get(cId)
        if (!c1) {
        	return notFound()
        }
        ReadOnlyRecord rec1
        if (authService.hasPermissionsForContact(cId)) {
            rec1 = c1.readOnlyRecord
        }
        else {
            Long scId = authService.getSharedContactIdForContact(c1.id)
            if (!scId) { return forbidden() }
            SharedContact sc1 = SharedContact.get(scId)
            if (!sc1) { return forbidden() }
            Result<ReadOnlyRecord> res = sc1.tryGetReadOnlyRecord()
            if (!res.success) { return forbidden() }
            rec1 = res.payload
        }
        Closure<Integer> count = { rec1.countFutureMessages() }
        Closure<List<ReadOnlyFutureMessage>> list = { Map opts -> rec1.getFutureMessages(opts) }
        respondWithMany(FutureMessage, count, list, params)
    }
    protected def listForTag(GrailsParameterMap params) {
    	Long ctId = params.long("tagId")
    	ContactTag ct1 = ContactTag.get(ctId)
    	if (!ct1) {
    		return notFound()
    	}
    	if (!authService.hasPermissionsForTag(ctId)) {
    		return forbidden()
    	}
        Closure<Integer> count = { ct1.record.countFutureMessages() }
        Closure<List<FutureMessage>> list = { Map opts -> ct1.record.getFutureMessages(opts) }
        respondWithMany(FutureMessage, count, list, params)
    }

    // Show
    // ----

    @RestApiMethod(description="Show specifics about a message")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
            paramType=RestApiParamType.PATH, description="Id of the message")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="403", description="You do not have permissions to view this message."),
        @RestApiError(code="404",  description="The requested message was not found.")
    ])
    @Transactional(readOnly=true)
    def show() {
        FutureMessage fMsg1 = FutureMessage.get(params.long("id"))
        if (fMsg1) {
            if (authService.hasPermissionsForFutureMessage(fMsg1.id)) {
                respond(fMsg1, [status:ResultStatus.OK.apiStatus])
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Save
    // ----

    @RestApiMethod(description="Create a new message for a contact or tag")
    @RestApiParams(params=[
        @RestApiParam(name="contactId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the contact to create for"),
        @RestApiParam(name="tagId", type="Number", required=true,
            paramType=RestApiParamType.QUERY, description="Id of the tag to create for"),
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key to convert times to,
            include schedule intervals, defaults to UTC if unspecified or invalid''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description='''Malformed JSON in request or \
            must specify either contact id or tag id but not both'''),
        @RestApiError(code="422", description="The updated fields created an invalid message."),
        @RestApiError(code="403", description="You do not permissions to create \
            a new message for this contact or tag."),
        @RestApiError(code="404",  description="The contact or tag to \
            add this message to was not found.")
    ])
    def save() {
        Map fInfo = getJsonPayload(FutureMessage, request)
        if (fInfo == null) { return }
        String tz = params.timezone as String
        if (!Helpers.exactly(1, ["contactId", "tagId"], params)) {
            badRequest()
        }
        else if (params.long("contactId")) {
            Long cId = params.long("contactId")
            if (!authService.exists(Contact, cId)) {
                return notFound()
            }
            // try to create, if allowed
            if (authService.hasPermissionsForContact(cId)) {
                respondWithResult(FutureMessage, futureMessageService.createForContact(cId, fInfo, tz))
            }
            else {
                Long sharedId = authService.getSharedContactIdForContact(cId)
                if (sharedId) {
                    respondWithResult(FutureMessage,
                        futureMessageService.createForSharedContact(sharedId, fInfo, tz))
                }
                else { forbidden() }
            }
        }
        else { // tag id
            Long ctId = params.long("tagId")
            if (!authService.exists(ContactTag, ctId)) {
                return notFound()
            }
            // try to create, if allowed
            if (authService.hasPermissionsForTag(ctId)) {
                respondWithResult(FutureMessage, futureMessageService.createForTag(ctId, fInfo, tz))
            }
            else { forbidden() }
        }
    }

    // Update
    // ------

    @RestApiMethod(description="Update an existing message")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number",
            paramType=RestApiParamType.PATH, description="Id of the message"),
        @RestApiParam(name="timezone", type="String", paramType=RestApiParamType.QUERY,
            required=false, description='''IANA zone info key to convert times to,
            include schedule intervals, defaults to UTC if unspecified or invalid''')
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="400", description="Malformed JSON in request."),
        @RestApiError(code="404", description="The requested message was not found."),
        @RestApiError(code="403", description="You do not have permission to modify this message."),
        @RestApiError(code="422", description="The updated fields created an invalid message.")
    ])
    def update() {
        Map fInfo = getJsonPayload(FutureMessage, request)
        if (fInfo == null) { return }
    	Long id = params.long("id")
        if (authService.exists(FutureMessage, id)) {
            if (authService.hasPermissionsForFutureMessage(id)) {
                String tz = params.timezone as String
                respondWithResult(FutureMessage, futureMessageService.update(id, fInfo, tz))
            }
            else { forbidden() }
        }
        else { notFound() }
    }

    // Delete
    // ------

    @RestApiMethod(description="Delete an existing message")
    @RestApiParams(params=[
        @RestApiParam(name="id", type="Number", paramType=RestApiParamType.PATH,
            description="Id of the message")
    ])
    @RestApiErrors(apierrors=[
        @RestApiError(code="404", description="The requested message was not found."),
        @RestApiError(code="403", description="You do not have permission to delete this message.")
    ])
    def delete() {
    	Long id = params.long("id")
        if (authService.exists(FutureMessage, id)) {
            if (authService.hasPermissionsForFutureMessage(id)) {
                respondWithResult(Void, futureMessageService.delete(id))
            }
            else { forbidden() }
        }
        else { notFound() }
    }
}
