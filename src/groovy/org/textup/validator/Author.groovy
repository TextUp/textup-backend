package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString
import org.textup.type.AuthorType

@GrailsCompileStatic
@ToString
@Validateable
class Author {
	Long id
    String name
    AuthorType type

    static constraints = {
    	id nullable: false
    	name nullable: false
    	type nullable: false
    }
}
