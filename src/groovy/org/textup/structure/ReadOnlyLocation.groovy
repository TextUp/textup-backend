package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface ReadOnlyLocation {
    Long getId()
    String getAddress()
    BigDecimal getLat()
    BigDecimal getLon()
}
