package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode

@GrailsTypeChecked
@EqualsAndHashCode
class ContactPhoneRecord extends PhoneRecord {

    Contact contact
    Phone sharedBy
    SharedContact sharedContact

    static constraints = {
        contact validator: { Contact val, ContactPhoneRecord obj ->
            if (val.context != this) {
                ["nonSymmetric"]
            }
        }
        sharedContact validator: { SharedContact val, ContactPhoneRecord obj ->
            if (val.context != this) {
                ["nonSymmetric"]
            }
        }
    }

    Result<Contactable> tryGetContactable() {
        isExpired() ?
            IOCUtils.resultFactory.success(sharedContact ?: contact) :
            IOCUtils.resultFactory.failWithValidationErrors("contactPhoneRecord.expired", ResultStatus.FORBIDDEN) // TODO
    }
}
