package org.textup

import grails.compiler.GrailsCompileStatic
import org.textup.type.AuthorType

@GrailsCompileStatic
interface Authorable {
    String getAuthorName()
    Long getAuthorId()
    AuthorType getAuthorType()
}
