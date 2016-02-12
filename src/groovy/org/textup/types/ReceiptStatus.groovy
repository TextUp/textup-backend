package org.textup.types

import org.textup.Constants
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum ReceiptStatus {
	FAILED,
	PENDING,
	BUSY,
	SUCCESS

	static String translate(String status) {
		if (status in Constants.FAILED_STATUSES) {
			FAILED
		}
		else if (status in Constants.PENDING_STATUSES) {
			PENDING
		}
		else if (status in Constants.FAILED_STATUSES) {
			BUSY
		}
		else {
			SUCCESS
		}
	}
}
