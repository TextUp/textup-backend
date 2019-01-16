package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.util.*

@GrailsTypeChecked
@Validateable
class TempRecordItem implements Validateable, Dehydratable<TempRecordItem.Dehydrated> {

	String text
	MediaInfo media
	Location location

	static constraints = { // default nullable: false
		text nullable: true, blank: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE,
			validator: { String val, TempRecordItem obj ->
				if (!obj.hasMedia() && !obj.location && !val) { ["atLeastOneRequired"] }
			}
		media nullable: true, cascadeValidation: true
		location nullable: true, cascadeValidation: true
	}

	static class Dehydrated implements Rehydratable<TempRecordItem> {

		String text
		Long mediaId
		Long locationId

        @Override
        Result<TempRecordItem> tryRehydrate() {
        	TempRecordItem temp1 = new TempRecordItem(text: text,
        		media: mediaId ? Media.get(mediaId) : null,
        		location: locationId ? Location.get(locationId) : null)
        	DomainUtils.tryValidate(temp1)
        }
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

	@Override
	TempRecordItem.Dehydrated dehydrate() {
		new TempRecordItem.Dehydrated(text: text, mediaId: media?.id, location: location?.id)
	}
}
