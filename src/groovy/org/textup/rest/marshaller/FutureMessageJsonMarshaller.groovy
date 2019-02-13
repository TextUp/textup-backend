package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class FutureMessageJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { ReadOnlyFutureMessage fMsg1 ->
        Map json = [:]
        json.with {
            id          = fMsg1.id
            isDone      = fMsg1.isReallyDone
            isRepeating = fMsg1.isRepeating
            language    = fMsg1.language?.toString()
            links       = MarshallerUtils.buildLinks(RestUtils.RESOURCE_FUTURE_MESSAGE, fMsg1.id)
            media       = fMsg1.readOnlyMedia
            message     = fMsg1.message
            notifySelf  = fMsg1.notifySelf
            startDate   = fMsg1.startDate
            type        = fMsg1.type.toString()
            whenCreated = fMsg1.whenCreated

            if (fMsg1.nextFireDate) nextFireDate = fMsg1.nextFireDate
			if (fMsg1.isRepeating) {
                if (fMsg1.endDate) endDate = fMsg1.endDate
				// repeating specific to simple schedule
				if (fMsg1 instanceof ReadOnlySimpleFutureMessage) {
                    repeatIntervalInDays                     = fMsg1.repeatIntervalInDays
                    if (fMsg1.repeatCount) repeatCount       = fMsg1.repeatCount
                    if (fMsg1.timesTriggered) timesTriggered = fMsg1.timesTriggered
				}
			}
        }

        RequestUtils.tryGet(RequestUtils.PHONE_RECORD_ID)
            .then { Long prId ->
                PhoneRecord pr1 = PhoneRecord.get(prId)
                if (pr1) {
                    if (pr1 instanceof GroupPhoneRecord) {
                        json.tag = pr1.id
                    }
                    else { json.contact = pr1.id }
                }
            }

        json
	}

	FutureMessageJsonMarshaller() {
		super(ReadOnlyFutureMessage, marshalClosure)
	}
}
