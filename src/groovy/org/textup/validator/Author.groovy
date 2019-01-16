package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.ToString
import org.textup.type.AuthorType

@GrailsTypeChecked
@ToString
@Validateable
class Author implements Validateable {
	Long id
    String name
    AuthorType type

    static constraints = {
    	id nullable: false
    	name nullable: false
    	type nullable: false
    }
}
