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
class IndividualPhoneRecord extends PhoneRecord {

    boolean isDeleted = false
    String name
    String note

    static hasMany = [numbers: ContactNumber]
    // `name` and `isDeleted` columns are shared with `GroupPhoneRecord`
    static mapping = {
        numbers fetch: "join", cascade: "all-delete-orphan"
        note column: "individual_note", type: "text"
    }
    static constraints = {
        name blank: true, nullable: true
        note blank: true, nullable: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
    }

    static Result<IndividualPhoneRecord> tryCreate(Phone p1) {
        Record.tryCreate()
            .then { Record rec1 ->
                IndividualPhoneRecord ipr1 = new IndividualPhoneRecord(phone: p1, record: rec1)
                DomainUtils.trySave(ipr1, ResultStatus.CREATED)
                    .ifFail { Result<?> failRes ->
                        rec1.delete()
                        failRes
                    }
            }
    }

    def beforeInsert() { normalizeNumberPreferences() }

    def beforeUpdate() { normalizeNumberPreferences() }

    // Methods
    // -------

    @Override
    boolean isActive() { super.isActive() && !isDeleted }

    @Override
    IndividualPhoneRecordWrapper toWrapper(PhoneRecord sharingOverride = null) {
        sharingOverride ?
            new IndividualPhoneRecordWrapper(this, sharingOverride.toPermissions(), sharingOverride) :
            new IndividualPhoneRecordWrapper(this, toPermissions())
    }

    Result<ContactNumber> mergeNumber(BasePhoneNumber bNum, int preference) {
        ContactNumber cNum = numbers?.find { it.number == bNum.number }
        if (cNum) {
            cNum.preference = preference
            DomainUtils.trySave(cNum)
        }
        else { ContactNumber.tryCreate(this, bNum, preference) }
    }

    Result<Void> deleteNumber(BasePhoneNumber bNum) {
        ContactNumber cNum = numbers?.find { it.number == bNum?.number }
        if (cNum) {
            removeFromNumbers(cNum)
        }
        DomainUtils.trySave(this).then { Result.void() }
    }

    IndividualPhoneRecordInfo toInfo() {
        IndividualPhoneRecordInfo.create(id, name, note, getSortedNumbers())
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name ?: numbers?.getAt(0)?.prettyPhoneNumber ?: "" }

    List<ContactNumber> getSortedNumbers() { numbers?.sort(false) ?: [] }

    // Helpers
    // -------

    protected void normalizeNumberPreferences() {
        getSortedNumbers().eachWithIndex { ContactNumber cn1, int pref -> cn1.preference = pref }
    }
}
