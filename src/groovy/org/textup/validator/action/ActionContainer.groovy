package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class ActionContainer<T extends BaseAction> implements CanValidate {

	final Object data
	final Class<T> clazz
	final List<? extends T> actions

	ActionContainer(Class<T> c1, Object d1) {
		clazz = c1
		data = d1
		// try building actions after setting data
		actions = Collections.<T>unmodifiableList(tryBuildActions())
	}

	static constraints = {
		data validator: { Object data ->
			if (data instanceof Collection) {
				Collection dataCollection = TypeUtils.to(Collection, data)
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

	static <T extends BaseAction> Result<List<T>> tryProcess(Class<T> clazz, Object data) {
		if (!data) {
			return IOCUtils.resultFactory.success([])
		}
		ActionContainer ac1 = new ActionContainer<>(clazz, data)
		ac1.validate() ?
			IOCUtils.resultFactory.success(ac1.actions) :
			IOCUtils.resultFactory.failWithValidationErrors(ac1.errors)
	}

	// Helpers
	// -------

	protected List<? extends T> tryBuildActions() {
		List<? extends T> actions = []
		if (clazz && validate(["data"])) {
			TypeUtils.to(Collection, data)?.each { Object obj ->
				T action = (clazz.newInstance() as T)
				action.update(TypeUtils.to(Map, obj))
				actions << action
			}
		}
		actions
	}
}
