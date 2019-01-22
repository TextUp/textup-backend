package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class FutureMessages {

    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0) }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    static Result<FutureMessage> mustFindForId(Long fId) {
        FutureMessage fMsg = FutureMessage.get(fId)
        if (fMsg) {
            IOCUtils.resultFactory.success(fMsg)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("futureMessageService.update.notFound", // TODO
                ResultStatus.NOT_FOUND, [fId])
        }
    }

    static Result<FutureMessage> mustFindForKey(String futureKey) {
        FutureMessage fMsg = FutureMessage.findByKeyName(futureKey)
        if (fMsg) {
            IOCUtils.resultFactory.success(fMsg)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("futureMessageService.execute.messageNotFound", // TODO
                ResultStatus.NOT_FOUND, [futureKey])
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<FutureMessage> buildForRecordIds(Collection<Long> recordIds) {
        new DetachedCriteria(FutureMessage)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
    }

    // Helpers
    // -------

    protected static DetachedCriteria<FutureMessage> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(FutureMessage).build {
            idEq(thisId)
            "in"("record", PhoneRecords.buildActiveForStaffId(authId)
                .build(PhoneRecords.returnsRecord())
        }
    }
}
