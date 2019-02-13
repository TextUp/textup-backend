package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class RecordItemRequestJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { RecordItemRequest iReq1 ->
		Map json = [:]
        json.with {
            maxAllowedNumItems = ControllerUtils.MAX_PAGINATION_MAX
            phoneName          = iReq1.mutablePhone.buildName()
            phoneNumber        = iReq1.mutablePhone.number
            totalNumItems      = iReq1.criteria.count()
        }
        AuthUtils.tryGetActiveAuthUser()
            .thenEnd { Staff authUser -> json.exportedBy = authUser.name }
        // fetching sections with appropriate pagination options
    	RequestUtils.tryGet(RequestUtils.PAGINATION_OPTIONS)
            .ifFail { json.sections = iReq1.buildSections() }
            .thenEnd { Map opts -> json.sections = iReq1.buildSections(opts) }
        // setting timestamps with appropriate
        RequestUtils.tryGet(RequestUtils.TIMEZONE)
            .thenEnd { String tz ->
                json.with {
                    startDate      = iReq1.buildFormattedStart(tz)
                    endDate        = iReq1.buildFormattedEnd(tz)
                    exportedOnDate = JodaUtils.CURRENT_TIME_FORMAT
                        .print(JodaUtils.toDateTimeWithZone(DateTime.now(), tz))
                }
            }
		json
	}

	RecordItemRequestJsonMarshaller() {
		super(RecordItemRequest, marshalClosure)
	}
}
