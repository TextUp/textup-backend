package org.textup

import groovy.transform.EqualsAndHashCode
import org.textup.validator.BasePhoneNumber
import grails.compiler.GrailsTypeChecked
import org.hibernate.Session
import groovy.transform.TypeCheckingMode

@Sortable(includes = ["preference"])
@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true, includes=["number", "preference"])
class ContactNumber extends BasePhoneNumber implements WithId, Saveable {

	Integer preference

    static belongsTo = [owner: IndividualPhoneRecord]
    static constraints = {
        number validator:{ String val ->
            if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
    }
}
