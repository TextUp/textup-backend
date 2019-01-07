package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.rest.NotificationStatus
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Contact implements Contactable, WithId {

    boolean isDeleted = false
    ContactPhoneRecord context
    ContactStatus status = ContactStatus.ACTIVE
    DateTime lastTouched = DateTime.now()
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    List<ContactNumber> numbers
    String name
    String note

    static hasMany = [numbers: ContactNumber]
    static mapping = {
        whenCreated type: PersistentDateTime
        lastTouched type: PersistentDateTime
        numbers lazy: false, cascade: "all-delete-orphan"
        context fetch: "join", cascade: "save-update"
    }
    static constraints = {
        name blank: true, nullable: true
        note blank: true, nullable: true, size: 1..1000
        context cascadeValidation: true
    }

    def beforeValidate() {
        tryReconcileNumberPreferences()
    }

    // Methods
    // -------

    Result<ContactNumber> mergeNumber(BasePhoneNumber bNum, int preference) {
        ContactNumber cNum = this.numbers?.find { it.number == bNum?.number }
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
        ContactNumber cNum = this.numbers?.find { it.number == bNum?.number }
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

    @Override
    Result<Record> tryGetRecord() { IOCUtils.resultFactory.success(context.record) }

    @Override
    Result<ReadOnlyRecord> tryGetReadOnlyRecord() { IOCUtils.resultFactory.success(context.record) }

    // Properties
    // ----------

    @Override
    String getSecureName() { name ?: numbers?.getAt(0)?.prettyPhoneNumber ?: "" }

    @Override
    String getPublicName() { StringUtils.buildInitials(name) }

    @Override
    List<ContactNumber> getSortedNumbers() { numbers?.sort(false) ?: [] }

    @Override
    Long getContactId() { id }

    @Override
    PhoneNumber getFromNum() { context.phone.number }

    @Override
    String getCustomAccountId() { context.phone.customAccountId }

    @Override
    ReadOnlyRecord getReadOnlyRecord() { contex.record }

    // Helpers
    // -------

    protected void tryReconcileNumberPreferences() {
        // autoincrement numbers' preference for new numbers if blank
        Collection<ContactNumber> initialNums = this.numbers?.findAll { !it.id && !it.preference }
        if (initialNums) {
            Collection<ContactNumber> existingNums = this.numbers - initialNums
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
