package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class GroupPhoneRecord extends PhoneRecord {

    boolean isDeleted = false
    PhoneRecordMembers members
    String hexColor = Constants.DEFAULT_BRAND_COLOR
    String name

    static mapping = {
        hexColor column: "group_hex_color"
        isDeleted column: "group_is_deleted"
        members fetch: "join", cascade: "all-delete-orphan"
        name column: "group_name"
    }
    static constraints = {
        hexColor validator: { String val ->
            if (!ValidationUtils.isValidHexCode(val)) { ["invalidHex"] }
        }
    }

    static Result<GroupPhoneRecord> tryCreate(Phone p1, String name) {
        Record.tryCreate()
            .then { Record rec1 -> PhoneRecordMembers.tryCreate().curry(rec1) }
            .then { Record rec1, PhoneRecordMembers prMembers ->
                GroupPhoneRecord gpr1 = new GroupPhoneRecord(name: name,
                    phone: p1,
                    record: rec1,
                    members: prMembers)
                DomainUtils.trySave(gpr1, ResultStatus.CREATED)
            }
    }

    // Methods
    // -------

    @Override
    boolean isActive() { super.isActive() && !isDeleted }

    @Override
    Collection<Record> buildRecords() {
        CollectionUtils.mergeUnique([[record], getActiveMembers()*.record])
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name }

    Collection<PhoneRecord> getActiveMembers() {
        members.phoneRecords?.findAll { PhoneRecord pr1 -> pr1.isActive() } ?: new ArrayList<PhoneRecord>()
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
