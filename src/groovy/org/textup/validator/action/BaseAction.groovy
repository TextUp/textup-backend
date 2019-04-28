package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode(includes = ["action"])
@GrailsTypeChecked
@Validateable
abstract class BaseAction implements CanValidate {

	String action

	static constraints =  {
		action validator: { String action, BaseAction obj ->
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
			// obtaining metaClass requires calling via `this`
			MetaProperty propertyInfo = this.metaClass.hasProperty(this, kStr)
			if (propertyInfo) {
				this[kStr] = TypeUtils.to(propertyInfo.type, v)
			}
		}
		this
	}

	boolean matches(String toMatch) { StringUtils.equalsIgnoreCase(action, toMatch) }

	// enable use of this class in switch statements
	// http://www.javaworld.com/article/2073225/scripting-jvm-languages/groovy-switch-on-steroids.html
	// NOTE: this method is only called when the case clause in the switch statement is
	// of type BaseAction. When matching against something else (for example, a String), this method
	// is NOT called!
	boolean isCase(BaseAction otherAction) { otherAction?.equals(this) }

	@Override
	String toString() { action?.toLowerCase() }
}
