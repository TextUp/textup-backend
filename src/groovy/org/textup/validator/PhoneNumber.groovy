package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Log4j
import org.textup.Result
import org.textup.util.IOCUtils

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true)
@Validateable
@Log4j
class PhoneNumber extends BasePhoneNumber {

    static constraints = {
        number nullable:false, validator:{ String val ->
	        if (!ValidationUtils.isValidPhoneNumber(val)) { ["format"] }
	    }
    }

    // Static creators
    // ---------------

    static PhoneNumber create(String num) {
        new PhoneNumber(number: num)
    }

    static Result<PhoneNumber> tryCreate(String num) {
        DomainUtils.tryValidate(PhoneNumber.create(num), ResultStatus.CREATED)
    }

    static Result<PhoneNumber> tryUrlDecode(String num) {
        try {
            String decodedNum = URLDecoder.decode(num, Constants.DEFAULT_CHAR_ENCODING)
            PhoneNumber.tryCreate(decodedNum)
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "tryUrlDecode", LogLevel.DEBUG)
        }
    }
}
