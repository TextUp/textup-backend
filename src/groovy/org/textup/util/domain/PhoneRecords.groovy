package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class PhoneRecords {

    static DetachedCriteria<PhoneRecord> forRecordIds(Collection<Long> recordIds) {
        new DetachedCriteria(PhoneRecord).build {
            CriteriaUtils.inList(delegate, "record.id", recordIds)
        }
    }

    static DetachedCriteria<PhoneRecord> forPhoneIds(Collection<Long> phoneIds) {
        new DetachedCriteria(PhoneRecord).build {
            CriteriaUtils.inList(delegate, "phone.id", recordIds)
        }
    }

    static Closure buildForPhoneId() {
        return {
            projections {
                property("phone.id")
            }
        }
    }

    static Closure buildForRecordId() {
        return {
            projections {
                property("record.id")
            }
        }
    }
}
