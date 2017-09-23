package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import org.textup.Helpers

@GrailsCompileStatic
@Validateable
abstract class BaseAction {

	String action

	static constraints =  {
		action validator:{ String action, BaseAction obj ->
			Collection<String> options = obj.getAllowedActions()
			if (!Helpers.inListIgnoreCase(action, options)) {
				["invalid", options]
			}
		}
	}

	// Methods
	// -------

	abstract Collection<String> getAllowedActions()

	boolean matches(String toMatch) {
		Helpers.toLowerCaseString(action) == Helpers.toLowerCaseString(toMatch)
	}

	// enable use of this class in switch statements
	// http://www.javaworld.com/article/2073225/scripting-jvm-languages/groovy-switch-on-steroids.html
	boolean isCase(BaseAction otherAction) {
		otherAction?.equals(this)
	}
	@Override
	boolean equals(Object other) {
		(other instanceof BaseAction) ? this.matches((other as BaseAction).action) : false
	}
	@Override
	String toString() {
		this.action
	}
}