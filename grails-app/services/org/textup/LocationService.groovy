package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@GrailsTypeChecked
@Transactional
class LocationService {

    Result<Location> create(TypeMap body) {
        Location.tryCreate(body.long("address"), body.double("lat"), body.double("lng"))
    }

    Result<Void> tryUpdate(Location loc1, TypeMap body) {
        if (loc1) {
            loc1.with {
                if (body.address) address = body.address
                if (body.lat != null) lat = body.double("lat")
                if (body.lng != null) lng = body.double("lng")
            }
            DomainUtils.trySave(loc1)
                .then { IOCUtils.resultFactory.success() }
        }
        else { IOCUtils.resultFactory.success() }
    }
}
