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

        AuthService authService = grailsApplication.mainContext.getBean(AuthService)

		Map json = [:]
        json.with {
            totalNumItems = itemRequest.countRecordItems()
            maxAllowedNumItems = Constants.MAX_PAGINATION_MAX
            exportedBy = authService.loggedInAndActive?.name
            phoneName = itemRequest.phone?.name
            phoneNumber = itemRequest.phone?.number?.prettyPhoneNumber
        }
        // fetching sections with appropriate pagination options
    	RequestUtils.tryGetFromRequest(RequestUtils.PAGINATION_OPTIONS)
            .end({ Map options = null -> json.sections = itemRequest.getSections(options)},
                { json.sections = itemRequest.getSections() })
        // setting timestamps with appropriate
        RequestUtils.tryGetFromRequest(RequestUtils.TIMEZONE)
            .end { String tz = null ->
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
