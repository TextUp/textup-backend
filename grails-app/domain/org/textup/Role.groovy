package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Role {

	String authority

	static constraints = {
		authority blank: false, unique: true
	}
    static mapping = {
        cache true
    }
}
