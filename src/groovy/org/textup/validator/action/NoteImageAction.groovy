package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Constants
import org.textup.Helpers
import org.textup.Result
import org.textup.ResultFactory
import org.textup.validator.UploadItem

// documented as [noteImageAction] in CustomApiDocs.groovy

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class NoteImageAction extends BaseAction {

	// required when adding
	String mimeType
	String data
	String checksum

	// required when removing
	String key

	static constraints = {
		action validator: { String action, NoteImageAction obj ->
			if (obj.matches(Constants.NOTE_IMAGE_ACTION_ADD)) {
				UploadItem uItem = new UploadItem(obj.properties)
                if (!uItem.validate()) {
                	Result res = obj.getResultFactory().failWithValidationErrors(uItem.errors)
                	["errorForAdd", res.errorMessages]
            	}
			}
			else { // Constants.NOTE_IMAGE_ACTION_REMOVE
				if (!obj.key) { ["missingForRemove"] }
			}
		}
		mimeType nullable:true, blank:true
		data nullable:true, blank:true
		checksum nullable:true, blank:true
		key nullable:true, blank:true
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.NOTE_IMAGE_ACTION_ADD, Constants.NOTE_IMAGE_ACTION_REMOVE]
	}

	// Helpers
	// -------

	protected ResultFactory getResultFactory() {
		Holders
			.applicationContext
			.getBean("resultFactory") as ResultFactory
	}
}