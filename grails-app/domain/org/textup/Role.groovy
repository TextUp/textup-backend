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

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

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
