package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
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
            DehydratedTempRecordItem dTemp1 = new DehydratedTempRecordItem(temp1.text,
                temp1.media?.id, temp1.location?.id)
            DomainUtils.tryValidate(dTemp1, ResultStatus.CREATED)
        }
    }

    // Methods
    // -------

    @Override
    Result<TempRecordItem> tryRehydrate() {
        TempRecordItem.tryCreate(text,
            mediaId ? MediaInfo.get(mediaId) : null,
            locationId ? Location.get(locationId) : null)
    }
}
