package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class PhoneRecords {

    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId ->
                int numAllowed = buildAllowed(authId)
                    .build { idEq(thisId) }
                    .count()
                AuthUtils.isAllowed(numAllowed > 0)
            }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    static DetachedCriteria<PhoneRecord> buildNotExpiredForRecordIds(Collection<Long> recordIds) {
        new DetachedCriteria(PhoneRecord)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
            .build(PhoneRecords.forNotExpired())
    }
    static DetachedCriteria<PhoneRecord> buildActiveForRecordIds(Collection<Long> recordIds) {
        new DetachedCriteria(PhoneRecord)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
            .build(PhoneRecords.forActive())
    }

    static DetachedCriteria<PhoneRecord> buildNotExpiredForPhoneIds(Collection<Long> phoneIds) {
        new DetachedCriteria(PhoneRecord)
            .build(PhoneRecords.forPhoneIds(phoneIds, false))
            .build(PhoneRecords.forNotExpired())
    }

    static DetachedCriteria<PhoneRecord> buildActiveForShareSourceIds(Collection<Long> sourceIds) {
        new DetachedCriteria(PhoneRecord)
            .build(PhoneRecords.forShareSourceIds(sourceIds))
            .build(PhoneRecords.forActive())
    }

    // Subqueries cannot include an `or` clause or else results in an NPE because of an existing bug.
    // Therefore, we have to first get the record ids via this method and then pass these record ids
    // into where we would have put a subquery. We can't use the closure-based workaround we did in
    // `Phones` because the active condition in this class ALSO has `or` clauses. If we were to try
    // to build all possible combinations of the phone ownerships AND the active conditions
    // this would have resulted in an infeasibly large number of subqueries.
    // see: https://github.com/grails/grails-data-mapping/issues/655
    static Collection<Long> findEveryAllowedRecordIdForStaffId(Long staffId) {
        if (staffId) {
            buildAllowed(staffId)
                .build(PhoneRecords.returnsRecordId())
                .list()
        }
        else { [] }
    }

    static Closure forOwnedOnly() {
        return {
            ne("class", PhoneRecord) // only contacts or tags that we own, not shared ones
        }
    }

    static Closure forNonGroupOnly() {
        return {
            ne("class", GroupPhoneRecord)
        }
    }

    static Closure forShareSourceIds(Collection<Long> sourceIds) {
        return {
            CriteriaUtils.inList(delegate, "shareSource.id", sourceIds)
        }
    }

    static Closure forPhoneIds(Collection<Long> phoneIds, boolean optional = false) {
        return {
            CriteriaUtils.inList(delegate, "phone.id", phoneIds, optional)
        }
    }

    static Closure forIds(Collection<Long> ids) {
        return {
            CriteriaUtils.inList(delegate, "id", ids)
        }
    }

    static Closure forVisibleStatuses() {
        return {
            "in"("status", PhoneRecordStatus.VISIBLE_STATUSES)
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

    static Closure returnsRecordId() {
        return {
            projections {
                property("record.id")
            }
        }
    }

    static Closure forActive() {
        return {
            "in"("status", PhoneRecordStatus.ACTIVE_STATUSES)
            ClosureUtils.compose(delegate, PhoneRecords.forNotExpired())
        }
    }

    static Closure forNotExpired() {
        return {
            or {
                isNull("isDeleted") // this column is null for `PhoneRecord`s
                eq("isDeleted", false) // all subclasses need to share this column
            }
            or {
                isNull("dateExpired") // not expired if null
                gt("dateExpired", JodaUtils.utcNow())
            }
            or {
                isNull("permission")
                ne("permission", SharePermission.NONE)
            }
            phone {
                ClosureUtils.compose(delegate, Phones.forActive())
            }
        }
    }

    // Helpers
    // -------

    protected static DetachedCriteria<PhoneRecord> buildAllowed(Long staffId) {
        new DetachedCriteria(PhoneRecord)
            .build(Phones.activeForPhonePropNameAndStaffId("phone", staffId))
            .build(PhoneRecords.forNotExpired())
    }
}
