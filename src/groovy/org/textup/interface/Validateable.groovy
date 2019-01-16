package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.springframework.validation.Errors

@GrailsTypeChecked
interface Validateable {

    boolean validate()
    Errors getErrors()
}
