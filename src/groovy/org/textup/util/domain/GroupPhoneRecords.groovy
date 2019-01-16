package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class GroupPhoneRecords {

    static Result<GroupPhoneRecord> mustFindForId(Long gprId) {
        GroupPhoneRecord gpr1 = GroupPhoneRecord.get(gprId)
        if (gpr1) {
            IOCUtils.resultFactory.success(gpr1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("tagService.update.notFound", // TODO
                ResultStatus.NOT_FOUND, [gprId])
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
                    CriteriaUtils.compose(delegate, PhoneRecords.forActive())
                }
            }
            .build(PhoneRecords.forActive())
    }

    static Closure forSort() {
        return { order("name", "desc") }
    }
}
