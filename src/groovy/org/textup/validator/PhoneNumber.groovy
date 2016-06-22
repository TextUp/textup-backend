package org.textup.validator

import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class PhoneNumber extends BasePhoneNumber {

    static constraints = {
        number nullable:false, validator:{ String val, PhoneNumber obj ->
	        if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
	    }
    }
}
