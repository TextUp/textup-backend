package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class AvailablePhoneNumber extends BasePhoneNumber {

    private static final String TYPE_EXISTING = "sid"
    private static final String TYPE_NEW = "region"

	final String info
	final String infoType

	static constraints = {
		infoType inList: [TYPE_EXISTING, TYPE_NEW]
        number phoneNumber: true
    }

    static Result<AvailablePhoneNumber> tryCreateExisting(String num, String sid) {
        AvailablePhoneNumber aNum = new AvailablePhoneNumber(number: num,
            info: sid,
            infoType: TYPE_EXISTING)
        DomainUtils.tryValidate(aNum, ResultStatus.CREATED)
    }

    static Result<AvailablePhoneNumber> tryCreateNew(String num, String country, String region) {
        AvailablePhoneNumber aNum = new AvailablePhoneNumber(number: num,
            info: region ? "${region}, ${country}" : country,
            infoType: TYPE_NEW)
        DomainUtils.tryValidate(aNum, ResultStatus.CREATED)
    }
}
