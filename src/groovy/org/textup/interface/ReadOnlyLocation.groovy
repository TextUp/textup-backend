package org.textup.interface

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface ReadOnlyLocation {
    Long getId()
    String getAddress()
    BigDecimal getLat()
    BigDecimal getLon()
}
