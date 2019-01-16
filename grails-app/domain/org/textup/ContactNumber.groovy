package org.textup

import groovy.transform.EqualsAndHashCode
import org.textup.validator.BasePhoneNumber
import grails.compiler.GrailsTypeChecked
import org.hibernate.Session
import groovy.transform.TypeCheckingMode

@Sortable(includes = ["preference"])
@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true, includes = ["number", "preference"])
class ContactNumber extends BasePhoneNumber implements WithId, Saveable<ContactNumber> {

	Integer preference

    static belongsTo = [owner: IndividualPhoneRecord]
    static constraints = {
        number validator:{ String val ->
            if (!ValidationUtils.isValidPhoneNumber(val)) { ["format"] }
        }
    }

    static Result<ContactNumber> tryCreate(IndividualPhoneRecord owner, BasePhoneNumber bNum,
        int preference) {

        ContactNumber cNum = new ContactNumber(preference: preference)
        cNum.number = bNum.number
        owner.addToNumbers(cNum)
        DomainUtils.trySave(cNum, ResultStatus.CREATED)
    }
}
