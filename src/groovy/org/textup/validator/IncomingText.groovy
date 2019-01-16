package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class IncomingText implements Validateable {

	String apiId
	String message
    Integer numSegments

	static constraints = {
		apiId nullable: false
		message nullable: false
        numSegments nullable: false, min: 0
	}

	void setMessage(String msg) {
		this.message = msg?.trim()
	}
}
