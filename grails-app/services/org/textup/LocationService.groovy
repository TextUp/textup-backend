package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class LocationService {

    Result<Location> tryCreateOrUpdateIfPresent(Location loc1, TypeMap body) {
        if (body) {
            loc1 ? tryUpdate(loc1, body) : tryCreate(body)
        }
        else { IOCUtils.resultFactory.success(loc1) }
    }

    Result<Location> tryCreate(TypeMap body) {
        Location.tryCreate(body?.string("address"), body?.double("lat"), body?.double("lng"))
    }

    Result<Location> tryUpdate(Location loc1, TypeMap body) {
        if (loc1 && body) {
            loc1.with {
                if (body.address) address = body.address
                if (body.lat != null) lat = body.double("lat")
                if (body.lng != null) lng = body.double("lng")
            }
            DomainUtils.trySave(loc1)
        }
        else { IOCUtils.resultFactory.success(loc1) }
    }
}
