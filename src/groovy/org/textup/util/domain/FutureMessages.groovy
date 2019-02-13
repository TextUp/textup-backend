package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class FutureMessages {

    @GrailsTypeChecked
    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0) }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    @GrailsTypeChecked
    static Result<FutureMessage> mustFindForId(Long fId) {
        FutureMessage fMsg = FutureMessage.get(fId)
        if (fMsg) {
            IOCUtils.resultFactory.success(fMsg)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("futureMessages.notFoundForId",
                ResultStatus.NOT_FOUND, [fId])
        }
    }

    @GrailsTypeChecked
    static Result<FutureMessage> mustFindForKey(String futureKey) {
        FutureMessage fMsg = FutureMessage.findByKeyName(futureKey)
        if (fMsg) {
            IOCUtils.resultFactory.success(fMsg)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("futureMessages.notFoundForKey",
                ResultStatus.NOT_FOUND, [futureKey])
        }
    }

    static DetachedCriteria<FutureMessage> buildForRecordIds(Collection<Long> recordIds) {
        new DetachedCriteria(FutureMessage)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
    }

    // Helpers
    // -------

    protected static DetachedCriteria<FutureMessage> buildForAuth(Long thisId, Long authId) {
        Collection<Long> recIds = PhoneRecords.findEveryAllowedRecordIdForStaffId(authId)
        new DetachedCriteria(FutureMessage).build {
            idEq(thisId)
            CriteriaUtils.inList(delegate, "record.id", recIds)
        }
    }
}
