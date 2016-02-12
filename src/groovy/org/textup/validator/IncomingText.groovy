package org.textup.validator

import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode
@Validateable
class IncomingText {

	String apiId
	String message

	static constraints = {
		apiId nullable: false
		message nullable: false
	}

	void setMessage(String msg) {
		this.message = msg?.trim()
	}
}
