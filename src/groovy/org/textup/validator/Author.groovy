package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@ToString
@Validateable
class Author implements CanValidate {
	Long id
    String name
    AuthorType type

    static constraints = {
    	id nullable: false
    	name nullable: false
    	type nullable: false
    }
}
