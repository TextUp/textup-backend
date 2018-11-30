package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
class RecordItemRequestJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { RecordItemRequest itemRequest ->
		Map json = [:]
        json.with {
            totalNumItems = itemRequest.countRecordItems()
            maxAllowedNumItems = Constants.MAX_PAGINATION_MAX
        }
		Result<Map> res = Utils.tryGetFromRequest(Constants.REQUEST_PAGINATION_OPTIONS)
      .logFail("RecordItemRequestJsonMarshaller: no available request", LogLevel.DEBUG)
    if (res.success) {
    	json.sections = itemRequest.getSections(res.payload)
    }
    else { json.sections = itemRequest.getSections() }
		json
	}

	RecordItemRequestJsonMarshaller() {
		super(RecordItemRequest, marshalClosure)
	}
}
