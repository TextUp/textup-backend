package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class PhoneRecordWrapperJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { PhoneRecordWrapper w1 ->
        Map json = [:]

        if (!PhoneRecordWrapper instanceof IndividualPhoneRecordWrapper) {
            log.error("`${w1.id}` is not an IndividualPhoneRecordWrapper")
            return json
        }
        if (!w1.permissions.canView()) {
            log.error("`${w1.id}` cannot be viewed")
            return json
        }

        DateTime lastTouched = w1.tryGetLastTouched().payload
        ReadOnlyPhone mutPhone1 = w1.tryGetReadOnlyMutablePhone().payload
        ReadOnlyPhone origPhone1 = w1.tryGetReadOnlyOriginalPhone().payload
        PhoneRecordStatus stat1 = w1.tryGetStatus().payload
        ReadOnlyRecord rec1 = w1.tryGetReadOnlyRecord().payload

        json.with {
            futureMessages     = FutureMessages.buildForRecordIds([rec1.id]).list()
            id                 = w1.id
            language           = rec1.language.toString()
            lastRecordActivity = rec1.lastRecordActivity
            links              = MarshallerUtils.buildLinks(RestUtils.RESOURCE_CONTACT, w1.id)
            name               = w1.tryGetSecureName().payload
            phone              = mutPhone1.id
            status             = stat1.toString()
            tags               = GroupPhoneRecords.buildForMemberIdsAndOptions([w1.id], mutPhone1.id)
            whenCreated        = w1.tryGetWhenCreated().payload

            if (stat1 == PhoneRecordStatus.UNREAD) {
                unreadInfo = UnreadInfo.create(rec1.id, lastTouched)
            }
        }

        if (PhoneRecordWrapper instanceof IndividualPhoneRecordWrapper) {
            IndividualPhoneRecordWrapper iw1 = w1 as IndividualPhoneRecordWrapper
            json.with {
                note    = iw1.tryGetNote().payload
                numbers = iw1.tryGetSortedNumbers().payload
            }
        }

        if (WrapperUtils.isSharedContact(w1)) {
            json.with {
                permission    = w1.permissions.level.toString()
                sharedByName  = origPhone1.buildName()
                sharedByPhone = origPhone1.id
            }
        }

        if (WrapperUtils.isContact(w1)) {
            json.sharedWith = PhoneRecords.buildActiveForShareSourceIds([w1.id]).list()*.toShareInfo()
        }

        json
    }

    PhoneRecordWrapperJsonMarshaller() {
        super(PhoneRecordWrapper, marshalClosure)
    }
}
