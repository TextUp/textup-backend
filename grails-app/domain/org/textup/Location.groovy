package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Location implements ReadOnlyLocation, WithId, CanSave<Location> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

	String address
    BigDecimal lat
    BigDecimal lng

    static constraints = {
        address nullable:false, blank:false
        lat validator: { BigDecimal l, Location obj ->
            if (l > 90 || l < -90) { ["location.lat.outOfBounds"] }
        }
        lng validator: { BigDecimal l, Location obj ->
            if (l > 180 || l < -180) { ["location.lng.outOfBounds"] }
        }
    }

    static Result<Location> tryCreate(String address, Double lat, Double lng) {
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
