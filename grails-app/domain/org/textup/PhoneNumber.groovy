package org.textup

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(callSuper=true)
class PhoneNumber extends TransientPhoneNumber {

    //number from superclass

    static constraints = {
    	number validator:{ val, obj ->
            if (val?.size() != 10) { ["format"] }
        }
    }

    /*
	Has many:
	*/

    ////////////////////
    // Helper methods //
    ////////////////////

    PhoneNumber copy() { new PhoneNumber(number:this.number) }
    static PhoneNumber copy(TransientPhoneNumber num) {
        new PhoneNumber(number:num?.number)
    }

    /////////////////////
    // Property Access //
    /////////////////////
}
