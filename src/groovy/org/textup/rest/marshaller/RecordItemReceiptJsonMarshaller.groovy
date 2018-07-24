package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class RecordItemReceiptJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { ReadOnlyRecordItemReceipt receipt ->
		[
			id: receipt.id,
			status: receipt.status.toString(),
			receivedBy: receipt.receivedBy.e164PhoneNumber
		]
	}

	RecordItemReceiptJsonMarshaller() {
		super(ReadOnlyRecordItemReceipt, marshalClosure)
	}
}
