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
		preference min: 0
		number phoneNumber: true
	}

	// Methods
	// -------

	PhoneNumber buildPhoneNumber() { PhoneNumber.create(number) }

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [MERGE, DELETE] }

	void setNumber(String num) {
		number = StringUtils.cleanPhoneNumber(num)
	}
}
