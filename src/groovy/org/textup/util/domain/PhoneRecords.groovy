package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class PhoneRecords {

    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0) }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    static Result<? extends PhoneRecord> mustFindForId(Long thisId) {
        PhoneRecord pr1 = PhoneRecord.get(thisId)
        if (pr1) {
            IOCUtils.resultFactory.success(pr1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("", // TODO
                ResultStatus.NOT_FOUND, [thisId])
        }
    }

    static DetachedCriteria<PhoneRecord> buildActiveForStaffId(Long staffId) {
        new DetachedCriteria(PhoneRecord)
            .build { "in"("phone", Phones.buildAllPhonesForStaffId(staffId)) }
            .build(PhoneRecords.forActive())
    }

    static DetachedCriteria<PhoneRecord> buildActiveForRecordIds(Collection<Long> recordIds) {
        new DetachedCriteria(PhoneRecord)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
            .build(PhoneRecords.forActive())
    }

    static DetachedCriteria<PhoneRecord> buildActiveForPhoneIds(Collection<Long> phoneIds) {
        new DetachedCriteria(PhoneRecord)
            .build(PhoneRecords.forPhoneIds(phoneIds), false)
            .build(PhoneRecords.forActive())
    }

    static Closure forShareSourceIds(Collection<Long> shareSourceIds) {
        return {
            CriteriaUtils.inList(delegate, "shareSource.id", shareSourceIds)
        }
    }

    static Closure forPhoneIds(Collection<Long> phoneIds, boolean optional) {
        return {
            CriteriaUtils.inList(delegate, "phone.id", phoneIds, optional)
        }
    }

    static Closure forIds(Collection<Long> ids) {
        return {
            CriteriaUtils.inList(delegate, "id", ids)
        }
    }

    static Closure returnsPhone() {
        return {
            projections {
                property("phone")
            }
        }
    }

    static Closure returnsRecord() {
        return {
            projections {
                property("record")
            }
        }
    }

    static Closure forActive() {
        return {
            eq("isDeleted", false) // TODO does this work?
            or {
                isNull("dateExpired") // not expired if null
                gt("dateExpired", DateTimeUtils.now())
            }
            phone {
                CriteriaUtils.compose(delegate, Phones.forActive())
            }
        }
    }

    // Helpers
    // -------

    protected static DetachedCriteria<PhoneRecord> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(PhoneRecord)
            .build {
                idEq(thisId)
                "in"("phone", Phones.buildAllPhonesForStaffId(authId))
            }
            .build(PhoneRecords.forActive())
    }
}
