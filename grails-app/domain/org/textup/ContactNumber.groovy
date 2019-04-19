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

// Grails Domain classes cannot apply the `@Sortable` without a compilation error
// Use `@EqualsAndHashCode` to ensure that addTo* and removeFrom* work properly

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
class ContactNumber extends BasePhoneNumber implements WithId, CanSave<ContactNumber>, Comparable<ContactNumber> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

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

    @Override
    int compareTo(ContactNumber cNum) { // first sort on preference, then phone number value
        preference <=> cNum?.preference ?: number <=> cNum?.number
    }
}
