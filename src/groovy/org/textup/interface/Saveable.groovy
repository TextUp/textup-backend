package org.textup.interface

import grails.compiler.GrailsTypeChecked
import org.springframework.validation.Errors

@GrailsTypeChecked
interface Saveable {

    public <T extends Saveable> T save()
    Errors getErrors()
}
