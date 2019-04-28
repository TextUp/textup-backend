package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
class GroupPhoneRecord extends PhoneRecord {

    boolean isDeleted = false
    PhoneRecordMembers members
    String hexColor = Constants.DEFAULT_BRAND_COLOR
    String name

    // `name` and `isDeleted` columns are shared with `IndividualPhoneRecord`
    static mapping = {
        hexColor column: "group_hex_color"
        members fetch: "join", cascade: "save-update"
    }
    static constraints = {
        hexColor validator: { String val ->
            if (!ValidationUtils.isValidHexCode(val)) {
                ["groupPhoneRecord.hexColor.invalidHex"]
            }
        }
    }

    static Result<GroupPhoneRecord> tryCreate(Phone p1, String name) {
        Record.tryCreate(p1?.language)
            .then { Record rec1 -> PhoneRecordMembers.tryCreate().curry(rec1) }
            .then { Record rec1, PhoneRecordMembers prMembers ->
                GroupPhoneRecord gpr1 = new GroupPhoneRecord(name: name,
                    phone: p1,
                    record: rec1,
                    members: prMembers)
                DomainUtils.trySave(gpr1, ResultStatus.CREATED)
                    .ifFailAndPreserveError {
                        rec1.delete()
                        prMembers.delete()
                    }
            }
    }

    // Methods
    // -------

    @Override
    boolean isNotExpired() { super.isNotExpired() && !isDeleted }

    @Override
    Collection<Record> buildRecords() {
        CollectionUtils.mergeUnique([[record], members.allActive*.record])
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name }
}
