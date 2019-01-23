package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.springframework.validation.Errors
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface CanSave<T> extends CanValidate {
    T save()
}
