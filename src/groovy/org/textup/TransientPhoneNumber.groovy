package org.textup

import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(callSuper=true)
@Validateable
class TransientPhoneNumber extends BasePhoneNumber {

    String number

	static constraints = {
    	number validator:{ val, obj ->
            if (val?.size() != 10) { ["format"] }
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    static TransientPhoneNumber copy(BasePhoneNumber num) {
        new TransientPhoneNumber(number:num?.number)
    }
}
