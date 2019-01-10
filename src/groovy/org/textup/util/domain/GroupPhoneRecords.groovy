package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class GroupPhoneRecords {

    static DetachedCriteria<GroupPhoneRecord> buildForPhoneIdAndOptions(Long phoneId, String name = null) {
        new DetachedCriteria(GroupPhoneRecord)
            .build {
                eq("phone.id", phoneId)
                if (name) {
                    eq("name", name)
                }
            }
            .build(PhoneRecords.forActive())
    }

    static DetachedCriteria<GroupPhoneRecord> buildForMemberIds(Collection<Long> memberIds) {
        new DetachedCriteria(GroupPhoneRecord)
            .build {
                members {
                    CriteriaUtils.inList(delegate, "id", memberIds)
                    PhoneRecords.forActive().setDelegate(delegate).call()
                }
            }
            .build(PhoneRecords.forActive())
    }

    static Closure forSort() {
        return { order("name", "desc") }
    }
}
