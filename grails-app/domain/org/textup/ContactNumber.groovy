package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import groovy.transform.TypeCheckingMode
import org.hibernate.Session
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

// [NOTE] Grails Domain classes cannot apply the `@Sortable` without a compilation error
// Use `@EqualsAndHashCode` to ensure that addTo* and removeFrom* work properly
// [NOTE] Only the generated `hashCode` is used. The generated `equals` is superceded by the
// overriden `compareTo` method. Therefore, ensure the fields in the annotation match the ones
// used in the compareTo implementation exactly
// [NOTE] using `id` in the `equals`, `hashCode`, `compareTo` calculations seems to break the
// dynamic `removeFrom` methods. Therefore, we exclude using `id` from these calculations

// Need to include `callSuper` because we can't include the `number` property directly since this is
// a property of the superclass. If we include `number` in the `includes` list without adding
// `callSuper` then `number` will be silently ignored
@EqualsAndHashCode(callSuper = true, includes = ["preference"])
@GrailsTypeChecked
class ContactNumber extends BasePhoneNumber implements WithId, CanSave<ContactNumber>, Comparable<ContactNumber> {

	Integer preference

    static belongsTo = [owner: IndividualPhoneRecord]
    static constraints = {
        number phoneNumber: true
    }

    static Result<ContactNumber> tryCreate(IndividualPhoneRecord ipr1, BasePhoneNumber bNum,
        Integer preference) {

        ContactNumber cNum = new ContactNumber(preference: preference, number: bNum?.number)
        ipr1?.addToNumbers(cNum)
        DomainUtils.trySave(cNum, ResultStatus.CREATED)
            .ifFailAndPreserveError { ipr1?.removeFromNumbers(cNum) }
    }

    // Methods
    // -------

    // [NOTE] the `==` operator in Groovy calls `compareTo` INSTEAD OF `equals` if present
    // see https://stackoverflow.com/a/9682512
    @Override
    int compareTo(ContactNumber cNum) { // first sort on preference, then phone number value
        preference <=> cNum?.preference ?: number <=> cNum?.number
    }
}
