package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Log4j

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
@Log4j
class PhoneNumber extends BasePhoneNumber {

    static constraints = {
        number nullable:false, validator:{ String val ->
	        if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
	    }
    }

    // Static creators
    // ---------------

    static PhoneNumber urlDecode(String number) {
        String decodedNum = number
        if (decodedNum) {
            try {
                decodedNum = URLDecoder.decode(number, "UTF-8")
            }
            catch (Throwable e) { log.debug("urlDecode: could not decode `${number}`") }
        }
        new PhoneNumber(number: decodedNum)
    }
}
