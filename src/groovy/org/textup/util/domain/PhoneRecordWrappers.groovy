package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class PhoneRecordWrappers {

    static Result<? extends PhoneRecordWrapper> mustFindForId(Long prId) {
        PhoneRecord pr1 = PhoneRecord.get(prId)
        if (pr1) {
            IOCUtils.resultFactory.success(pr1.toWrapper())
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("phoneRecordWrappers.notFound",
                ResultStatus.NOT_FOUND, [prId])
        }
    }
}
