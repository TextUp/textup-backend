package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.util.Holders
import grails.validation.Validateable
import org.textup.*

@GrailsCompileStatic
@Validateable
class MergeGroupItem {

	String numberAsString
	Collection<Long> contactIds = [] // initialize to empty collection to set off minSize constraint

	private Collection<Contact> mergeWith // dependent on setting contactIds

	static constraints = {
		numberAsString validator:{ String val ->
	        if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
	    }
	    contactIds minSize:1
	}

	// Property access
	// ---------------

	void setNumber(PhoneNumber pNum) {
		this.numberAsString = pNum.number
	}
	PhoneNumber getNumber() {
		new PhoneNumber(number:this.numberAsString)
	}

	void setContactIds(Collection<Long> cIds) {
		this.contactIds = cIds
		this.mergeWith = Contact
			.getAll(cIds as Iterable<Serializable>)
			.findAll { Contact c1 -> c1 != null  }
	}

	Collection<Contact> getMergeWith() {
		this.mergeWith ?: []
	}
}