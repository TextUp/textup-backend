package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable

@GrailsTypeChecked
@EqualsAndHashCode
@Sortable(includes = ["whenCreated"])
class PhoneNumberHistory {

    DateTime endTime
    final DateTime whenCreated
    final String numberAsString

    static constraints = {
        endTime nullable: true, validator: { DateTime val, PhoneNumberHistory obj ->
            if (val?.isBefore(obj.whenCreated) || val?.isEqual(obj.whenCreated)) {
                ["endBeforeStart"]
            }
        }
        numberAsString nullable: true, blank: true, phoneNumber: true
    }

    static Result<PhoneNumberHistory> tryCreate(DateTime created, BasePhoneNumber bNum) {
        PhoneNumberHistory nh1 = new PhoneNumberHistory(numberAsString: bNum?.number,
            whenCreated: DateTimeUtils.atStartOfMonth(created))
        DomainUtils.trySave(nh1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    boolean includes(Integer month, Integer year) {
        try {
            DateTime dt = DateTime.now()
                .withMonthOfYear(month)
                .withYear(year)
            if (dt.isEqual(whenCreated) || dt.isAfter(whenCreated)) {
                !endTime || dt.isEqual(endTime) || dt.isBefore(endTime)
            }
            else { false }
        }
        catch (Throwable e) {
            log.debug("includes: ${e.message}")
            false
        }
    }

    // Properties
    // ----------

    PhoneNumber getNumberIfPresent() { PhoneNumber.tryCreate(numberAsString).payload }

    void setEndTime(DateTime dt) { endTime = DateTimeUtils.atEndOfMonth(dt) }
}
