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
        Location.create(body.long("address"), body.double("lat"), body.double("lng"))
    }

    Result<Location> tryUpdate(Location loc1, TypeMap body) {
        loc1.with {
            if (body.address) address = body.address
            if (body.lat != null) lat = body.double("lat")
            if (body.lng != null) lng = body.double("lng")
        }
        DomainUtils.trySave(loc1)
    }
}
