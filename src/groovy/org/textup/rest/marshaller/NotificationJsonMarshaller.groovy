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
class NotificationJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { Notification notif1 ->
        PhoneOwnership own1 = notif1.mutablePhone.owner
        Map json = [:]
        json.with {
            id          = own1.ownerId
            name        = own1.buildName()
            phoneNumber = notif1.mutablePhone.number
            type        = own1.type.toString()
        }
        RequestUtils.tryGet(RequestUtils.STAFF_ID)
            .then { Object staffId -> Staffs.mustFindForId(TypeUtils.to(Long, staffId)) }
            .ifFailAndPreserveError { json.details = notif1.details }
            .thenEnd { Staff s1 ->
                ReadOnlyOwnerPolicy rop1 = OwnerPolicies.findReadOnlyOrDefaultForOwnerAndStaff(own1, s1)
                json.details = notif1.buildDetailsWithAllowedItemsForOwnerPolicy(rop1)
                json.putAll(DomainUtils.instanceProps(NotificationInfo.create(rop1, notif1)))
            }
        json
	}

	NotificationJsonMarshaller() {
		super(Notification, marshalClosure)
	}
}
