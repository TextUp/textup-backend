package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.TypeCheckingMode
import org.textup.*
import org.textup.structure.*
import org.textup.util.*

@GrailsTypeChecked
@Validateable
abstract class BaseAction implements CanValidate {

	String action

	private String _allowedAction // returned in toString to allow for appropriate switch matching

	static constraints =  {
		action validator:{ String action, BaseAction obj ->
			Collection<String> options = obj.getAllowedActions()
			if (!CollectionUtils.inListIgnoreCase(action, options)) {
				["invalid", options]
			}
		}
	}

	// Methods
	// -------

	abstract Collection<String> getAllowedActions()

	@GrailsTypeChecked(TypeCheckingMode.SKIP)
	BaseAction update(Map<?, ?> dataMap) {
		// set properties from map, ignoring the nonexistent properties
		// which will throw a MissingPropertyException if we directly
		// pass the data map into the newInstance constructor
		dataMap?.each { Object k, Object v ->
			String kStr = k?.toString()
			MetaProperty propertyInfo = hasProperty(kStr)
			if (propertyInfo) {
				this[kStr] = TypeConversionUtils.to(propertyInfo.type, v)
			}
		}
		this
	}

	@Override
	boolean matches(String toMatch) { StringUtils.equalsIgnoreCase(action, toMatch) }

	// enable use of this class in switch statements
	// http://www.javaworld.com/article/2073225/scripting-jvm-languages/groovy-switch-on-steroids.html
	// NOTE: this method is only called when the case clause in the switch statement is
	// of type BaseAction. When matching against something else (for example, a String), this method
	// is NOT called!
	@Override
	boolean isCase(BaseAction otherAction) {
		otherAction?.equals(this)
	}

	@Override
	boolean equals(Object other) {
		(other instanceof BaseAction) ? matches((other as BaseAction).action) : false
	}

	@Override
	String toString() {
		_allowedAction
	}

	// Properties
	// ----------

	void setAction(String newAction) {
		action = newAction
		// also set case-appropriate action for correct switch-base matching, if possible
		_allowedAction = getAllowedActions()
			.find { String str1 -> StringUtils.equalsIgnoreCase(str1, newAction) } ?: newAction
	}
}
