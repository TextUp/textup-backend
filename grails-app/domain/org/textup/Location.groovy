package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode

@GrailsTypeChecked
@EqualsAndHashCode
class Location implements ReadOnlyLocation, WithId, Saveable<Location> {

	String address
    BigDecimal lat
    BigDecimal lng

    static constraints = {
        address nullable:false, blank:false
        lat validator: { BigDecimal l, Location obj ->
            if (l > 90 || l < -90) { ["outOfBounds"] }
        }
        lng validator: { BigDecimal l, Location obj ->
            if (l > 180 || l < -180) { ["outOfBounds"] }
        }
    }

    static Result<Location> tryCreate(String address, Number lat, Number lng) {
        Location loc1 = new Location(address: address, lat: lat, lng: lng)
        DomainUtils.trySave(loc1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    Location tryDuplicatePersistentState() {
        Location dup = new Location(address: getPersistentValue("address"),
            lat: getPersistentValue("lat"),
            lng: getPersistentValue("lng"))
        if (dup.validate()) {
            dup
        }
        else { // if persistent state is not valid, then this obj has not been persisted yet
            log.debug("tryDuplicatePersistentState: could not duplicate: ${dup.errors}")
            dup.discard()
            null
        }
    }
}
