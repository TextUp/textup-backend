package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Constants
import org.textup.Helpers
import org.textup.Result
import org.textup.ResultFactory
import org.textup.type.PhoneOwnershipType
import org.textup.validator.PhoneNumber

// documented as [phoneAction] in CustomApiDocs.groovy

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class PhoneAction extends BaseAction {

	// required for transfer
	Long id // id of the staff/team to transfer this phone to
	String type // type of entity (staff versus team) to transfer to

	String number // required when creating new phone with specified number
	String numberId // required when creating new phone with existing number

	static constraints = {
		id nullable:true, validator: { Long val, PhoneAction obj ->
			// existence check for this id happens already in Phone.transferTo
			if (obj.matches(Constants.PHONE_ACTION_TRANSFER) && !val) {
				["requiredForTransfer"]
			}
		}
		type nullable:true, blank:true, validator: { String val, PhoneAction obj ->
			if (obj.matches(Constants.PHONE_ACTION_TRANSFER)) {
				if (!val) {
					return ["requiredForTransfer"]
				}
				Collection<String> options = PhoneOwnershipType.values().collect { it.toString() }
				if (!Helpers.inListIgnoreCase(val, options)) {
					return ["invalid", options]
				}
			}
		}
		number nullable:true, blank:true, validator: { String val, PhoneAction obj ->
			if (obj.matches(Constants.PHONE_ACTION_NEW_NUM_BY_NUM)) {
				if (!val) {
					return ["requiredForChangeToNewNumber"]
				}
				PhoneNumber pNum = new PhoneNumber(number:val)
				if (!pNum.validate()) {
					Result res = obj.getResultFactory().failWithValidationErrors(pNum.errors)
					return ["invalid", res.errorMessages]
				}
			}
		}
		numberId nullable:true, blank:true, validator: { String val, PhoneAction obj ->
			if (obj.matches(Constants.PHONE_ACTION_NEW_NUM_BY_ID) && !val) {
				["requiredForChangeToExistingNumber"]
			}
		}
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.PHONE_ACTION_DEACTIVATE, Constants.PHONE_ACTION_TRANSFER,
			Constants.PHONE_ACTION_NEW_NUM_BY_NUM, Constants.PHONE_ACTION_NEW_NUM_BY_ID]
	}

	// Methods
	// -------

	PhoneOwnershipType getTypeAsEnum() {
		Helpers.<PhoneOwnershipType>convertEnum(PhoneOwnershipType, this.type)
	}

	PhoneNumber getPhoneNumber() {
		new PhoneNumber(number:this.number)
	}

	// Property access
	// ---------------

	void setNumber(String num) {
		// clean number before validate
		this.number = (new PhoneNumber(number:num)).number
	}

	// Helpers
	// -------

	protected ResultFactory getResultFactory() {
		Holders
			.applicationContext
			.getBean("resultFactory") as ResultFactory
	}
}