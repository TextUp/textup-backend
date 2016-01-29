package org.textup.validator

import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(callSuper=true)
@Validateable
class PhoneNumber extends BasePhoneNumber {

    static constraints = {
        number shared: 'phoneNumber'
    }
}
