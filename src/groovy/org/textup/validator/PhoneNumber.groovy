package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Log4j
import org.textup.Result
import org.textup.util.IOCUtils

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

    static PhoneNumber create(String num) {
        new PhoneNumber(number: num)
    }

    static Result<? extends PhoneNumber> createAndValidate(String num) {
        PhoneNumber pNum = PhoneNumber.create(num)
        if (pNum.validate()) {
            IOCUtils.resultFactory.success(pNum)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(pNum.errors) }
    }

    static PhoneNumber urlDecode(String num) {
        String decodedNum = num
        if (decodedNum) {
            try {
                decodedNum = URLDecoder.decode(num, "UTF-8")
            }
            catch (Throwable e) { log.debug("urlDecode: could not decode `${num}`") }
        }
        PhoneNumber.create(decodedNum)
    }
}
