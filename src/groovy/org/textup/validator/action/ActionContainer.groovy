package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Helpers
import org.textup.Result
import org.textup.ResultFactory

@GrailsCompileStatic
@EqualsAndHashCode
@Validateable
class ActionContainer {

	Object data

	ActionContainer(Object data) {
		this.data = data
	}

	static constraints = {
		data validator: { Object data ->
			if (data instanceof Collection) {
				Collection dataCollection = Helpers.to(Collection, data)
				for (Object dataObj in dataCollection) {
					if (!(dataObj instanceof Map)) {
						return ["emptyOrNotAMap"]
					}
				}
			}
			else { ["emptyOrNotACollection"] }
		}
	}

	// Methods
	// -------

	public <T extends BaseAction> List<T> validateAndBuildActions(Class<T> clazz) {
		List<T> actions = []
		if (!validate()) {
			return actions
		}
		if (!clazz) { // even if clazz is null, we still want to validate
			return actions
		}
		ResultFactory resultFactory = getResultFactory()
		Collection dataCollection = Helpers.to(Collection, data)
		List<String> errorMessages = []
		if (dataCollection) {
			for (Object dataObj in dataCollection) {
				Map dataMap = Helpers.to(Map, dataObj)
				if (dataMap) {
					T action = (clazz.newInstance() as T)
					// set properties from map, ignoring the nonexistent properties
					// which will throw a MissingPropertyException if we directly
					// pass the data map into the newInstance constructor
					dataMap.each { Object k, Object v ->
						String kStr = k?.toString()
						MetaProperty propertyInfo = action.hasProperty(kStr)
						if (propertyInfo) {
							action[kStr] = Helpers.to(propertyInfo.type, v)
						}
					}
					if (action.validate()) { actions << action }
					else {
						Result res = resultFactory.failWithValidationErrors(action.errors)
						errorMessages += res.errorMessages
					}
				}
			}
		}
		if (errorMessages) {
			this.errors.reject("actionContainer.invalidActions", [errorMessages] as Object[],
				"Invalid action items")
		}
		actions
	}

	// Helpers
	// -------

	protected ResultFactory getResultFactory() {
		Holders
			.applicationContext
			.getBean("resultFactory") as ResultFactory
	}
}
