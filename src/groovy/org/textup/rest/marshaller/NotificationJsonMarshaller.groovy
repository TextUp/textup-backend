package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.validator.*

@GrailsTypeChecked
class NotificationJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { Notification notif1 ->
        Map json = [:]
        json.with {
            id          = notif1.mutablePhone.owner.ownerId
            name        = notif1.mutablePhone.owner.buildName()
            phoneNumber = notif1.mutablePhone.number
            type        = notif1.mutablePhone.owner.type.toString()
        }
        RequestUtils.tryGetFromRequest(RequestUtils.OWNER_POLICY_ID)
            .ifFail { json.details = notif1.items }
            .thenEnd { OwnerPolicy op1 ->
                json.details = notif1.buildAllowedItemsForOwnerPolicy(op1)
                json.putAll(NotificationInfo.create(op1, notif1).properties)
            }
        json
	}

	NotificationJsonMarshaller() {
		super(Notification, marshalClosure)
	}
}
