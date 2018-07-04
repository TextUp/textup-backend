package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
interface ReadOnlyLocation {
    Long getId()
    String getAddress()
    BigDecimal getLat()
    BigDecimal getLon()
}
