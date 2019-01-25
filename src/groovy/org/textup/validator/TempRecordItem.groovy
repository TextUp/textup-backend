package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class TempRecordItem implements CanValidate {

	String text
	MediaInfo media
	Location location

	static constraints = {
		text nullable: true, blank: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE,
			validator: { String val, TempRecordItem obj ->
				if (!obj.hasMedia() && !obj.location && !val) { ["atLeastOneRequired"] }
			}
		media nullable: true, cascadeValidation: true
		location nullable: true, cascadeValidation: true
	}

	static Result<TempRecordItem> tryCreate(String text, MediaInfo mInfo, Location loc1) {
		TempRecordItem temp1 = new TempRecordItem(text: text, media: mInfo, location: loc1)
		DomainUtils.tryValidate(temp1, ResultStatus.CREATED)
	}

	// Methods
	// -------

	boolean hasMedia() { media?.isEmpty() == false }

    // text can be read and audio clips can be played over calls
    boolean supportsCall() { text || media?.getMediaElementsByType(MediaType.AUDIO_TYPES) }
}
