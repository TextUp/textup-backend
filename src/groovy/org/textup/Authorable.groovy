package org.textup

import grails.compiler.GrailsTypeChecked
import org.textup.type.AuthorType

@GrailsTypeChecked
interface Authorable {
    String getAuthorName()
    Long getAuthorId()
    AuthorType getAuthorType()
}
