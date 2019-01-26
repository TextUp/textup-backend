package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
@TupleConstructor(includeSuperProperties = true, includeFields = true)
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
        AvailablePhoneNumber aNum = new AvailablePhoneNumber(num, sid, TYPE_EXISTING)
        DomainUtils.tryValidate(aNum, ResultStatus.CREATED)
    }

    static Result<AvailablePhoneNumber> tryCreateNew(String num, String country, String region) {
        String info = region ? "${region}, ${country}".toString() : country
        AvailablePhoneNumber aNum = new AvailablePhoneNumber(num, info, TYPE_NEW)
        DomainUtils.tryValidate(aNum, ResultStatus.CREATED)
    }
}
