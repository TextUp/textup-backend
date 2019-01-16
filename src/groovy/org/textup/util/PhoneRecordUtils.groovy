package org.textup.validator

import grails.compiler.GrailsTypeChecked

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
            .then { List<IndividualPhoneRecordWrapper> wrappers ->
                ResultGroup.collect(wrappers) { IndividualPhoneRecordWrapper w1 ->
                        w1.trySetStatusIfNotBlocked(PhoneRecordStatus.UNREAD)
                    }
                    .toEmptyResult(true)
                    .curry(wrappers)
            }
            .logFail("tryMarkUnread: marking unread")
            .then { IOCUtils.resultFactory.success(wrappers) }
    }
}
