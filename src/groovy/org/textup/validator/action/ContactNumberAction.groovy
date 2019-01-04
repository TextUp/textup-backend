package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.util.*
import org.textup.validator.*

// documented as [numberAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class ContactNumberAction extends BaseAction {

	int preference = 0
	String number

	static constraints =  {
		preference min:0
		number validator:{ String val, ContactNumberAction obj  ->
	        if (!obj.matches(Constants.NUMBER_ACTION_DELETE) &&
	        	!(val?.toString() ==~ /^(\d){10}$/)) {

	        	["format"]
	        }
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
		number = StringUtils.cleanPhoneNumber(num)
	}

	PhoneNumber getPhoneNumber() {
		PhoneNumber.create(number)
	}
}
