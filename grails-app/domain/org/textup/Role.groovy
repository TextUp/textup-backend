package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Role {

	String authority

	static mapping = {
		cache true
	}

	static constraints = {
		authority blank: false, unique: true
	}
}
