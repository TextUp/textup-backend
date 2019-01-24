package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Role implements CanSave<Role>, WithId {

	String authority

	static constraints = {
		authority blank: false, unique: true
	}
    static mapping = {
        cache true
    }

    static Result<Role> tryCreate(String authority) {
        DomainUtils.trySave(new Role(authority: authority), ResultStatus.CREATED)
    }
}
