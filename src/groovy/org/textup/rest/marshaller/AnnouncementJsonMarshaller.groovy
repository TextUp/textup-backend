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
class AnnouncementJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { FeaturedAnnouncement fa1 ->
        Map json = [:]
        json.with {
            expiresAt   = fa1.expiresAt
            id          = fa1.id
            isExpired   = fa1.isExpired
            links       = MarshallerUtils.buildLinks(RestUtils.RESOURCE_ANNOUNCEMENT, fa1.id)
            message     = fa1.message
            receipts    = AnnouncementInfo.create(fa1)
            whenCreated = fa1.whenCreated

            if (fa1.phone.owner.type == PhoneOwnershipType.INDIVIDUAL) {
                staff = fa1.phone.owner.ownerId
            }
            else {
                team = fa1.phone.owner.ownerId
            }
        }
        json
    }

    AnnouncementJsonMarshaller() {
        super(FeaturedAnnouncement, marshalClosure)
    }
}
