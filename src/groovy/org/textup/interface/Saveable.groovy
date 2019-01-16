package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.springframework.validation.Errors

@GrailsTypeChecked
interface Saveable<T> extends Validateable {
    T save()
}
