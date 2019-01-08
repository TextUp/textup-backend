package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class GroupPhoneRecords {

    static Result<GroupPhoneRecord> create(Phone p1, String name) {
        Records.create()
            .then { Record rec1 ->
                Utils.trySave(new GroupPhoneRecord(name: name, phone: p1, record: rec1))
            }
    }

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
