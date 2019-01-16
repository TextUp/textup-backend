package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

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
