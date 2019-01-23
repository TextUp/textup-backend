package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import groovy.transform.TypeCheckingMode
import org.hibernate.Session
import org.textup.validator.BasePhoneNumber

@Sortable(includes = ["preference"])
@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true, includes = ["number", "preference"])
class ContactNumber extends BasePhoneNumber implements WithId, Saveable<ContactNumber> {

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
}
