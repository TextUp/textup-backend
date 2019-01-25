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
class DehydratedTempRecordItem implements CanValidate, Rehydratable<TempRecordItem> {

    final String text
    final Long mediaId
    final Long locationId

    static constraints = {
        text nullable: true, blank: true
        mediaId nullable: true
        locationId nullable: true
    }

    static Result<DehydratedTempRecordItem> tryCreate(TempRecordItem temp1) {
        DomainUtils.tryValidate(temp1).then {
            DehydratedTempRecordItem dTemp1 = new DehydratedTempRecordItem(text: temp1.text,
                mediaId: temp1.media?.id, locationId: temp1.location?.id)
            DomainUtils.tryValidate(dTemp1, ResultStatus.CREATED)
        }
    }

    // Methods
    // -------

    @Override
    Result<TempRecordItem> tryRehydrate() {
        TempRecordItem temp1 = new TempRecordItem(text: text,
            media: mediaId ? MediaInfo.get(mediaId) : null,
            location: locationId ? Location.get(locationId) : null)
        DomainUtils.tryValidate(temp1)
    }
}
