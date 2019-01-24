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

    Result<Location> create(TypeMap body) {
        Location.tryCreate(body.string("address"), body.double("lat"), body.double("lng"))
    }

    Result<Void> tryUpdate(Location loc1, TypeMap body) {
        if (loc1) {
            loc1.with {
                if (body.address) address = body.address
                if (body.lat != null) lat = body.double("lat")
                if (body.lng != null) lng = body.double("lng")
            }
            DomainUtils.trySave(loc1)
                .then { Result.void() }
        }
        else { Result.void() }
    }
}
