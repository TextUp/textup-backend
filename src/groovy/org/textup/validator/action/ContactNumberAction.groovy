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

	static final String MERGE = "merge"
	static final String DELETE = "delete"

	int preference = 0
	String number

	static constraints =  {
		preference min:0
		number validator:{ String val, ContactNumberAction obj  ->
	        if (!obj.matches(ContactNumberAction.DELETE) &&
	        	!ValidationUtils.isValidPhoneNumber(val)) {

	        	["format"]
	        }
	    }
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[ContactNumberAction.MERGE, ContactNumberAction.DELETE]
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
