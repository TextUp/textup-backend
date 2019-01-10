package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
class Role {

	String authority

	static constraints = {
		authority blank: false, unique: true
	}
    static mapping = {
        cache true
    }

    static Result<Role> create(String authority) {
        DomainUtils.trySave(new Role(authority: authority))
    }
}
