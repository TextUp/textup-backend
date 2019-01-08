package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode

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
        hexColor validator: { String val -> // String must be a valid hex color
            if (!(val ==~ /^#(\d|\w){3}/ || val ==~ /^#(\d|\w){6}/)) { ["invalidHex"] }
        }
        members validator: { Collection<PhoneRecord> val ->
            if (val.any { PhoneRecord pr1 -> pr1.instanceOf(GroupPhoneRecord) }) {
                ["nestingNotSupported"]
            }
        }
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name }

    Collection<PhoneRecord> getActiveMembers() {
        members?.findAll { PhoneRecord pr1 -> pr1.toPermissions().isNotExpired() } ?:
            new ArrayList<PhoneRecord>()
    }

    // Can't move to static class because Grails manages this relationship so no direct queries
    Collection<PhoneRecord> getMembersByStatus(Collection<PhoneRecordStatus> statuses) {
        if (statuses) {
            HashSet<PhoneRecordStatus> statusesToFind = new HashSet<>(statuses)
            members?.findAll { PhoneRecord pr1 -> statusesToFind.contains(pr1.status) }
        }
        else { members }
    }
}
