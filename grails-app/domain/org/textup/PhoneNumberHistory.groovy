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

// [NOTE] Grails Domain classes cannot apply the `@Sortable` without a compilation error
// [NOTE] Only the generated `hashCode` is used. The generated `equals` is superceded by the
// overriden `compareTo` method. Therefore, ensure the fields in the annotation match the ones
// used in the compareTo implementation exactly

@EqualsAndHashCode(includes = ["whenCreated", "endTime", "id"])
@GrailsTypeChecked
class PhoneNumberHistory implements CanSave<PhoneNumberHistory>, WithId, Comparable<PhoneNumberHistory> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    DateTime endTime
    DateTime whenCreated
    String numberAsString

    static mapping = {
        endTime type: PersistentDateTime
        whenCreated type: PersistentDateTime
    }
    static constraints = {
        endTime nullable: true, validator: { DateTime val, PhoneNumberHistory obj ->
            if (val?.isBefore(obj.whenCreated) || val?.isEqual(obj.whenCreated)) {
                ["phoneNumberHistory.endTime.endBeforeStart"]
            }
        }
        numberAsString nullable: true, blank: true, phoneNumber: true
    }

    static Result<PhoneNumberHistory> tryCreate(DateTime created, BasePhoneNumber bNum) {
        PhoneNumberHistory nh1 = new PhoneNumberHistory(numberAsString: bNum?.number,
            whenCreated: created)
        DomainUtils.trySave(nh1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    boolean includes(Integer month, Integer year) {
        try {
            if (month == null || year == null) {
                return false
            }
            DateTime dt = JodaUtils.utcNow()
                .withMonthOfYear(month)
                .withYear(year)
            DateTime startOfMonth = JodaUtils.atStartOfMonth(whenCreated)
            if (dt.isEqual(startOfMonth) || dt.isAfter(startOfMonth)) {
                DateTime endOfMonth = JodaUtils.atEndOfMonth(endTime)
                !endOfMonth || dt.isEqual(endOfMonth) || dt.isBefore(endOfMonth)
            }
            else { false }
        }
        catch (Throwable e) {
            log.debug("includes: ${e.message}")
            false
        }
    }

    // [NOTE] the `==` operator in Groovy calls `compareTo` INSTEAD OF `equals` if present
    // see https://stackoverflow.com/a/9682512
    @Override
    int compareTo(PhoneNumberHistory nh1) { // first whenCreated, then endDate (nulls come first)
        whenCreated <=> nh1?.whenCreated ?: endTime <=> nh1?.endTime ?: id <=> nh1?.id
    }

    // Properties
    // ----------

    PhoneNumber getNumberIfPresent() { PhoneNumber.tryCreate(numberAsString).payload }
}
