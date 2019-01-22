package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class RecordItemRequestJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        RecordItemRequest itemRequest ->

		Map json = [:]
        json.with {
            totalNumItems = itemRequest.countRecordItems()
            maxAllowedNumItems = Constants.MAX_PAGINATION_MAX
            phoneName = itemRequest.phone?.owner?.buildName()
            phoneNumber = itemRequest.phone?.number?.prettyPhoneNumber
        }
        AuthUtils.tryGetAuthUser()
            .then { String authUser -> json.exportedBy = authUser.name }
        // fetching sections with appropriate pagination options
    	RequestUtils.tryGetFromRequest(RequestUtils.PAGINATION_OPTIONS)
            .ifFail { json.sections = itemRequest.buildSections() }
            .thenEnd { TypeMap opts = null -> json.sections = itemRequest.buildSections(opts) }
        // setting timestamps with appropriate
        RequestUtils.tryGetFromRequest(RequestUtils.TIMEZONE)
            .thenEnd { String tz = null ->
                json.with {
                    startDate = itemRequest.start
                        ? DateTimeUtils.FILE_TIMESTAMP_FORMAT.print(
                            DateTimeUtils.toDateTimeWithZone(itemRequest.start, tz))
                        : "beginning"
                    endDate = itemRequest.end
                        ? DateTimeUtils.FILE_TIMESTAMP_FORMAT.print(
                            DateTimeUtils.toDateTimeWithZone(itemRequest.end, tz))
                        : "end"
                    exportedOnDate = DateTimeUtils.CURRENT_TIME_FORMAT.print(
                        DateTimeUtils.toDateTimeWithZone(DateTime.now(), tz))
                }
            }
		json
	}

	RecordItemRequestJsonMarshaller() {
		super(RecordItemRequest, marshalClosure)
	}
}
