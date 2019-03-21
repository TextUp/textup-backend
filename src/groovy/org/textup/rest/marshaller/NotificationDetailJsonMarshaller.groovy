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
class NotificationDetailJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { NotificationDetail nd1 ->
        Map json = [:]
        json.with {
            id    = nd1.wrapper.id
            name  = nd1.wrapper.tryGetSecureName().payload
            isTag = WrapperUtils.isTag(nd1.wrapper)
        }
        RequestUtils.tryGet(RequestUtils.STAFF_ID)
            .then { Object staffId -> Staffs.mustFindForId(TypeUtils.to(Long, staffId)) }
            .then { Staff s1 -> nd1.wrapper.tryGetMutablePhone().curry(s1) }
            .ifFailAndPreserveError { json.items = nd1.items }
            .thenEnd { Staff s1, Phone p1 ->
                ReadOnlyOwnerPolicy rop1 = OwnerPolicies
                    .findReadOnlyOrDefaultForOwnerAndStaff(p1.owner, s1)
                json.items = nd1.buildAllowedItemsForOwnerPolicy(rop1)
            }
        json
	}

	NotificationDetailJsonMarshaller() {
		super(NotificationDetail, marshalClosure)
	}
}
