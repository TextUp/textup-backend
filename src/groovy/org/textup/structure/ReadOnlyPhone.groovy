package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
interface ReadOnlyPhone extends WithId {

    String buildName()

    PhoneNumber getNumber()
}
