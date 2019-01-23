package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class PhoneRecordWrappers {

    static Result<PhoneRecordWrapper> mustFindForId(Long prId) {
        PhoneRecord pr1 = PhoneRecord.get(prId)
        if (pr1) {
            IOCUtils.resultFactory.success(pr1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("", // TODO
                ResultStatus.NOT_FOUND, [prId])
        }
    }
}
