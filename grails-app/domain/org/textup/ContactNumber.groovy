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

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true, includes = ["number", "preference"])
class ContactNumber extends BasePhoneNumber implements WithId, CanSave<ContactNumber>, Comparable<ContactNumber> {

	Integer preference

    static belongsTo = [owner: IndividualPhoneRecord]
    static constraints = {
        number phoneNumber: true
    }

    static Result<ContactNumber> tryCreate(IndividualPhoneRecord owner, BasePhoneNumber bNum,
        int preference) {

        ContactNumber cNum = new ContactNumber(preference: preference)
        cNum.number = bNum.number
        owner.addToNumbers(cNum)
        DomainUtils.trySave(cNum, ResultStatus.CREATED)
    }

    // Methods
    // -------

    @Override
    int compareTo(ContactNumber cNum) { // first sort on preference, then phone number value
        preference <=> cNum?.preference ?: number <=> cNum?.number
    }
}
