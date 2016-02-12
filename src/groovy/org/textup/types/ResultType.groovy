package org.textup.types

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum ResultType {
	SUCCESS,
	VALIDATION,
	MESSAGE,
	MESSAGE_LIST_STATUS,
	MESSAGE_STATUS,
	THROWABLE
}
