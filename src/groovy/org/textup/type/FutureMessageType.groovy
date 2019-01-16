package org.textup.type

import grails.compiler.GrailsTypeChecked
import org.textup.type.RecordItemType

@GrailsTypeChecked
enum FutureMessageType {
	CALL(RecordItemType.CALL),
	TEXT(RecordItemType.TEXT)

	private final RecordItemType recordType

	FutureMessageType(RecordItemType type) {
		recordType = type
	}

	RecordItemType toRecordItemType() { recordType }
}
