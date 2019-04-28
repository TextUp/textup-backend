package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

// `includeSuperProperties = true` in `@TupleConstructor` seem to type check unreliably if the
// superclass is abstract

@GrailsTypeChecked
@TupleConstructor(includeFields = true)
@Validateable
class AvailablePhoneNumber extends BasePhoneNumber {

    static final String TYPE_EXISTING = "sid"
    static final String TYPE_NEW = "region"

	final String info
	final String infoType

	static constraints = {
		infoType inList: [TYPE_EXISTING, TYPE_NEW]
        number phoneNumber: true
    }

    static Result<AvailablePhoneNumber> tryCreateExisting(String num, String sid) {
        AvailablePhoneNumber aNum = new AvailablePhoneNumber(sid, TYPE_EXISTING)
        aNum.number = num
        DomainUtils.tryValidate(aNum, ResultStatus.CREATED)
    }

    static Result<AvailablePhoneNumber> tryCreateNew(String num, String country, String region) {
        String info = region ? "${region}, ${country}".toString() : country
        AvailablePhoneNumber aNum = new AvailablePhoneNumber(info, TYPE_NEW)
        aNum.number = num
        DomainUtils.tryValidate(aNum, ResultStatus.CREATED)
    }

    // Methods
    // -------

    @Override
    boolean equals(Object obj) {
        obj instanceof AvailablePhoneNumber ?
            super.equals(obj) && info?.equals(obj.info) && infoType?.equals(obj.infoType) :
            false
    }

    @Override
    int hashCode() { "${number}${info}${infoType}".toString().hashCode() }
}
