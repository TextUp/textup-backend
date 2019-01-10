package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode

@GrailsTypeChecked
@EqualsAndHashCode
class IndividualPhoneRecord extends PhoneRecord {

    boolean isDeleted = false
    String name
    String note

    static hasMany = [numbers: ContactNumber]
    static mapping = {
        numbers fetch: "join", cascade: "all-delete-orphan"
        isDeleted column: "individual_is_deleted"
        name column: "individual_name"
        note column: "individual_note", type: "text"
    }
    static constraints = {
        numbers minSize: 1
        name blank: true, nullable: true
        note blank: true, nullable: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
    }

    static Result<IndividualPhoneRecordWrapper> create(Phone p1, List<? extends BasePhoneNumber> bNums = []) {
        Record.create()
            .then { Record rec1 ->
                IndividualPhoneRecord ipr1 = new IndividualPhoneRecord(phone: p1, record: rec1)
                // need to save before adding numbers so that the domain is assigned an
                // ID to be associated with the ContactNumbers to avoid a TransientObjectException
                DomainUtils.trySave(ipr1)
            }
            .then { IndividualPhoneRecord ipr1 ->
                ResultGroup<ContactNumber> resGroup = new ResultGroup<>()
                bNums.unique().eachWithIndex { BasePhoneNumber bNum, int preference ->
                    resGroup << ipr1.mergeNumber(bNum, preference)
                }
                if (resGroup.anyFailures) {
                    IOCUtils.resultFactory.failWithGroup(resGroup)
                }
                else { IOCUtils.resultFactory.success(ipr1, ResultStatus.CREATED) }
            }
    }

    def beforeValidate() {
        tryReconcileNumberPreferences()
    }

    // Methods
    // -------

    @Override
    IndividualPhoneRecordWrapper toWrapper(PhoneRecord sharingOverride = null) {
        sharingOverride ?
            new IndividualPhoneRecordWrapper(this, sharingOverride.toPermissions(), sharingOverride)
            new IndividualPhoneRecordWrapper(this, toPermissions())
    }

    Result<ContactNumber> mergeNumber(BasePhoneNumber bNum, int preference) {
        ContactNumber cNum = numbers?.find { it.number == bNum?.number }
        if (!cNum) {
            cNum = new ContactNumber()
            cNum.update(bNum)
            addToNumbers(cNum)
        }
        cNum.preference = preference
        if (cNum.save()) {
            IOCUtils.resultFactory.success(cNum)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(cNum.errors) }
    }

    Result<Void> deleteNumber(BasePhoneNumber bNum) {
        ContactNumber cNum = numbers?.find { it.number == bNum?.number }
        if (cNum) {
            removeFromNumbers(cNum)
            cNum.delete()
            IOCUtils.resultFactory.success()
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("contact.numberNotFound",
                ResultStatus.NOT_FOUND, [bNum?.prettyPhoneNumber])
        }
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name ?: numbers?.getAt(0)?.prettyPhoneNumber ?: "" }

    List<ContactNumber> getSortedNumbers() { numbers?.sort(false) ?: [] }

    // Helpers
    // -------

    protected void tryReconcileNumberPreferences() {
        // autoincrement numbers' preference for new numbers if blank
        Collection<ContactNumber> initialNums = numbers?.findAll { !it.id && !it.preference }
        if (initialNums) {
            Collection<ContactNumber> existingNums = numbers - initialNums
            int greatestPref = 0
            if (existingNums) {
                ContactNumber greatestPrefNum = existingNums.max { it.preference }
                greatestPref = greatestPrefNum.preference
            }
            initialNums.eachWithIndex { ContactNumber cn, int i ->
                cn.preference = greatestPref + i + 1 // zero-indexed
            }
        }
    }
}
