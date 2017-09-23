package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Constants
import org.textup.validator.PhoneNumber

// documented as [numberAction] in CustomApiDocs.groovy
@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class ContactNumberAction extends BaseAction {

	int preference = 0
	String number

	static constraints =  {
		preference min:0
		number validator:{ String val ->
	        if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
	    }
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.NUMBER_ACTION_MERGE, Constants.NUMBER_ACTION_DELETE]
	}

	// Property access
	// ---------------

	void setNumber(String num) {
		// clean number before validate
		this.number = (new PhoneNumber(number:num)).number
	}
}