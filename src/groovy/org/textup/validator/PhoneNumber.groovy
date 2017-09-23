package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class PhoneNumber extends BasePhoneNumber {

    static constraints = {
        number nullable:false, validator:{ String val ->
	        if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
	    }
    }
}
