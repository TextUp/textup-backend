package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
class RecordItemReceiptInfoJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { RecordItemReceiptInfo stat1 ->
		[
			success: stat1.success,
			pending: stat1.pending,
			busy: stat1.busy,
			failed: stat1.failed
		]
	}

	RecordItemReceiptInfoJsonMarshaller() {
		super(RecordItemReceiptInfo, marshalClosure)
	}
}
