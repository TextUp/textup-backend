package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

// Grails Domain classes cannot apply the `@Sortable` without a compilation error

@EqualsAndHashCode
@GrailsTypeChecked
class PhoneNumberHistory implements CanSave<PhoneNumberHistory>, WithId, Comparable<PhoneNumberHistory> {

    DateTime endTime
    final DateTime whenCreated
    final String numberAsString

    static mapping = {
        endTime type: PersistentDateTime
        whenCreated type: PersistentDateTime
    }
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
            whenCreated: JodaUtils.atStartOfMonth(created))
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

    @Override
    int compareTo(PhoneNumberHistory nh1) { // first whenCreated, then endDate (nulls come first)
        whenCreated <=> nh1?.whenCreated ?: endTime <=> nh1?.endTime
    }

    // Properties
    // ----------

    PhoneNumber getNumberIfPresent() { PhoneNumber.tryCreate(numberAsString).payload }

    void setEndTime(DateTime dt) { endTime = JodaUtils.atEndOfMonth(dt) }
}
