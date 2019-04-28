package org.textup.type

import grails.compiler.GrailsTypeChecked

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
