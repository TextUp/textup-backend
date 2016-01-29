package org.textup.validator

import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
@Validateable
class IncomingText {

	String apiId
	String message

	static constraints = {
	}

	void setMessage(String msg) {
		this.message = msg?.toLowerCase()?.trim()
	}
}
