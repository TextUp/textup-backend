package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.util.*

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class ActionContainer<T extends BaseAction> {

	Object data
	Class<T> clazz
	List<T> actions = []

	ActionContainer(Class<T> clazz, Object data) {
		data = data
		clazz = clazz
	}

	static constraints = {
		data validator: { Object data ->
			if (data instanceof Collection) {
				Collection dataCollection = TypeConversionUtils.to(Collection, data)
				for (Object dataObj in dataCollection) {
					if (!(dataObj instanceof Map)) {
						return ["emptyOrNotAMap"]
					}
				}
			}
			else { ["emptyOrNotACollection"] }
		}
		actions cascadeValidation: true
	}

	// Property access
	// ---------------

	void setData(Object obj) {
		data = obj
		actions = tryBuildActions()
	}

	protected List<T> tryBuildActions() {
		List<T> actions = []
		if (validate(["data"])) {
			TypeConversionUtils.to(Collection, data)?.each { Object obj ->
				T action = (clazz.newInstance() as T)
				action.update(TypeConversionUtils.to(Map, obj))
				actions << action
			}
		}
		actions
	}
}
