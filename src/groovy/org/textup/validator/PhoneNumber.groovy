package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

// Inherits `equals` and `hashCode` defined in superclass

@GrailsTypeChecked
@Log4j
@Validateable
class PhoneNumber extends BasePhoneNumber {

    static constraints = {
        number phoneNumber: true
    }

    static PhoneNumber copy(BasePhoneNumber bNum) {
        PhoneNumber.create(bNum?.number)
    }

    static Result<PhoneNumber> tryUrlDecode(String num) {
        try {
            String decodedNum = num ?
                URLDecoder.decode(num, Constants.DEFAULT_CHAR_ENCODING) :
                null
            PhoneNumber.tryCreate(decodedNum)
        }
        catch (Throwable e) { IOCUtils.resultFactory.failWithThrowable(e) }
    }

    static Result<PhoneNumber> tryCreate(String num) {
        DomainUtils.tryValidate(PhoneNumber.create(num), ResultStatus.CREATED)
    }

    static PhoneNumber create(String num) {
        new PhoneNumber(number: num)
    }
}
