package org.textup.types

import grails.compiler.GrailsCompileStatic
import org.textup.types.RecordItemType

@GrailsCompileStatic
enum FutureMessageType {
	CALL(RecordItemType.CALL),
	TEXT(RecordItemType.TEXT)

	private final RecordItemType recordType
	FutureMessageType(RecordItemType type) {
		this.recordType = type
	}

	RecordItemType toRecordItemType() { this.recordType }
}
