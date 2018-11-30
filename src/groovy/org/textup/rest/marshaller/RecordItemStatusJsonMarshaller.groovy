package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
class RecordItemStatusJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { RecordItemStatus stat1 ->
		[
			success: stat1.success,
			pending: stat1.pending,
			busy: stat1.busy,
			failed: stat1.failed
		]
	}

	RecordItemStatusJsonMarshaller() {
		super(RecordItemStatus, marshalClosure)
	}
}
