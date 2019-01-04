package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
interface WithName {
    String getSecureName()
    String getPublicName()
}
