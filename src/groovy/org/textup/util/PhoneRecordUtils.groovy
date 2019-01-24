package org.textup.validator

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class PhoneRecordUtils {

    static Map<Long, Collection<GroupPhoneRecord>> buildMemberIdToGroups(Collection<GroupPhoneRecord> groups) {
        Map<Long, Collection<GroupPhoneRecord>> idToGroups = [:]
            .withDefault { [] as Collection<GroupPhoneRecord> }
        groups.each { GroupPhoneRecord gpr1 ->
            gpr1.activeMembers.each { PhoneRecord pr1 -> idToGroups[pr1.id] << gpr1 }
        }
        idToGroups
    }

    static Result<List<IndividualPhoneRecordWrapper>> tryMarkUnread(Phone p1, PhoneNumber pNum) {
        IndividualPhoneRecordWrappers.tryFindEveryByNumbers(p1, [pNum], true)
            .then { List<IndividualPhoneRecordWrapper> wraps ->
                ResultGroup
                    .collect(wraps) { IndividualPhoneRecordWrapper w1 ->
                        w1.trySetStatusIfNotBlocked(PhoneRecordStatus.UNREAD)
                    }
                    .toEmptyResult(true)
                    .curry(wraps)
            }
            .logFail("tryMarkUnread")
            .then { List<IndividualPhoneRecordWrapper> wraps ->
                IOCUtils.resultFactory.success(wraps)
            }
    }
}
