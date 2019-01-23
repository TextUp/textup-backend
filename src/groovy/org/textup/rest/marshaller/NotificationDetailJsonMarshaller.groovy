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
            items = nd1.items
            name  = nd1.wrapper.tryGetSecureName().payload

            if (WrapperUtils.isTag(nd1.wrapper)) {
                tag = nd1.id
            }
            else { contact = nd1.id }
        }
        json
	}

	NotificationDetailJsonMarshaller() {
		super(NotificationDetail, marshalClosure)
	}
}
