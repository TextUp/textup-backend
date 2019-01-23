package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode
class GroupPhoneRecord extends PhoneRecord {

    boolean isDeleted = false
    String name
    String hexColor = Constants.DEFAULT_BRAND_COLOR

    static hasMany = [members: PhoneRecord]
    static mapping = {
        members fetch: "join"
        isDeleted column: "group_is_deleted"
        name column: "group_name"
        hexColor column: "group_hex_color"
    }
    static constraints = {
        hexColor validator: { String val ->
            if (!ValidationUtils.isValidHexCode(val)) { ["invalidHex"] }
        }
        members validator: { Collection<PhoneRecord> val ->
            if (val.any { PhoneRecord pr1 -> pr1.instanceOf(GroupPhoneRecord) }) {
                ["nestingNotSupported"]
            }
        }
    }

    static Result<GroupPhoneRecord> tryCreate(Phone p1, String name) {
        Record.tryCreate()
            .then { Record rec1 ->
                GroupPhoneRecord gpr1 = new GroupPhoneRecord(name: name, phone: p1, record: rec1)
                DomainUtils.trySave(gpr1, ResultStatus.CREATED)
            }
    }

    // Methods
    // -------

    @Override
    boolean isActive() { super.isActive() && !isDeleted }

    @Override
    Collection<Record> buildRecords() {
        CollectionUtils.mergeUnique([record], getActiveMembers()*.record)
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name }

    Collection<PhoneRecord> getActiveMembers() {
        members?.findAll { PhoneRecord pr1 -> pr1.isActive() } ?: new ArrayList<PhoneRecord>()
    }

    // Can't move to static class because Grails manages this relationship so no direct queries
    Collection<PhoneRecord> getMembersByStatus(Collection<PhoneRecordStatus> statuses) {
        if (statuses) {
            HashSet<PhoneRecordStatus> statusesToFind = new HashSet<>(statuses)
            getActiveMembers().findAll { PhoneRecord pr1 -> statusesToFind.contains(pr1.status) }
        }
        else { getActiveMembers() }
    }
}
