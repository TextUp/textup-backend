package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.type.PhoneOwnershipType
import org.textup.util.*
import org.textup.validator.PhoneNumber

// documented as [phoneAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true)
@Validateable
class PhoneAction extends BaseAction {

	static final String DEACTIVATE = "deactivate"
	static final String TRANSFER = "transfer"
	static final String NEW_NUM_BY_NUM  = "numbynum"
	static final String NEW_NUM_BY_ID = "numbyid"

	// required for transfer
	Long id // id of the staff/team to transfer this phone to
	String type // type of entity (staff versus team) to transfer to

	String number // required when creating new phone with specified number
	String numberId // required when creating new phone with existing number

	static constraints = {
		id nullable:true, validator: { Long val, PhoneAction obj ->
			// existence check for this id happens already in Phone.transferTo
			if (obj.matches(TRANSFER) && !val) {
				["requiredForTransfer"]
			}
		}
		type nullable:true, blank:true, validator: { String val, PhoneAction obj ->
			if (obj.matches(TRANSFER)) {
				if (!val) {
					return ["requiredForTransfer"]
				}
				Collection<String> options = PhoneOwnershipType.values().collect { it.toString() }
				if (!CollectionUtils.inListIgnoreCase(val, options)) {
					return ["invalid", options]
				}
			}
		}
		number nullable:true, blank:true, validator: { String val, PhoneAction obj ->
			if (obj.matches(NEW_NUM_BY_NUM)) {
				if (!val) {
					return ["requiredForChangeToNewNumber"]
				}
				PhoneNumber pNum = new PhoneNumber(number:val)
				if (!pNum.validate()) {
					Result res = IOCUtils.resultFactory.failWithValidationErrors(pNum.errors)
					return ["invalid", res.errorMessages]
				}
			}
		}
		numberId nullable:true, blank:true, validator: { String val, PhoneAction obj ->
			if (obj.matches(NEW_NUM_BY_ID) && !val) {
				["requiredForChangeToExistingNumber"]
			}
		}
	}

	// Methods
	// -------

	PhoneOwnershipType buildPhoneOwnershipType() {
		TypeConversionUtils.convertEnum(PhoneOwnershipType, type)
	}

	PhoneNumber buildPhoneNumber() { PhoneNumber.create(number) }

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [DEACTIVATE, TRANSFER, NEW_NUM_BY_NUM, NEW_NUM_BY_ID] }

	void setNumber(String num) { number = StringUtils.cleanPhoneNumber(num) }
}
