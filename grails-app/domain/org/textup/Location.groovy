package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*

@GrailsCompileStatic
@EqualsAndHashCode
@RestApiObject(name="Location", description="A location")
class Location implements ReadOnlyLocation {

    @RestApiObjectField(description = "Human readable address of the location")
	String address
    @RestApiObjectField(
        description = "Latitude of the location",
        allowedType =  "Number")
    BigDecimal lat
    @RestApiObjectField(
        description = "Longitude of the location",
        allowedType =  "Number")
    BigDecimal lon

    static constraints = {
        address nullable:false, blank:false
        lat validator: { BigDecimal l, Location obj ->
            if (l > 90 || l < -90) { ["outOfBounds"] }
        }
        lon validator: { BigDecimal l, Location obj ->
            if (l > 180 || l < -180) { ["outOfBounds"] }
        }
    }

    Location tryDuplicatePersistentState() {
        Closure doGet = { String propName -> this.getPersistentValue(propName) }
        Location dup = new Location(address: doGet("address"),
            lat: doGet("lat"),
            lon: doGet("lon"))
        if (dup.validate()) {
            dup
        }
        else { // if persistent state is not valid, then this obj has not been persisted yet
            log.debug("Location.tryDuplicatePersistentState: could not duplicate: ${dup.errors}")
            dup.discard()
            null
        }
    }

    @Override
    String toString() { this.address }
}
