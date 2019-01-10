package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class AvailablePhoneNumber extends BasePhoneNumber {

	String info
	String infoType

	static constraints = {
		info nullable:false
		infoType nullable:false, inList:["sid", "region"]
        number nullable:false, validator:{ String val ->
	        if (!ValidationUtils.isValidPhoneNumber(val)) { ["format"] }
	    }
    }

    // Property access
    // ---------------

    void setPhoneNumber(String pNum) {
    	super.setNumber(pNum)
    }
    String getPhoneNumber() {
    	this.number
    }

    void setSid(String sid) {
    	this.info = sid
    	this.infoType = "sid"
    }
    void setRegion(String region) {
    	this.info = region
    	this.infoType = "region"
    }
}
