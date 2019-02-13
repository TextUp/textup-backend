package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class GroupPhoneRecords {

    @GrailsTypeChecked
    static Result<GroupPhoneRecord> mustFindForId(Long gprId) {
        GroupPhoneRecord gpr1 = GroupPhoneRecord.get(gprId)
        if (gpr1) {
            IOCUtils.resultFactory.success(gpr1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("groupPhoneRecords.notFound",
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

    static DetachedCriteria<GroupPhoneRecord> buildForMemberIdsAndOptions(Collection<Long> memberIds,
        Long phoneId = null) {

        new DetachedCriteria(GroupPhoneRecord)
            .build {
                members {
                    phoneRecords {
                        CriteriaUtils.inList(delegate, "id", memberIds)
                        ClosureUtils.compose(delegate, PhoneRecords.forActive())
                    }
                }
                if (phoneId) {
                    eq("phone.id", phoneId)
                }
            }
            .build(PhoneRecords.forActive())
    }

    static Closure forSort() {
        return { order("name", "desc") }
    }
}
