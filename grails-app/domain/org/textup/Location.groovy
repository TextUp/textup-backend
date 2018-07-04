package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import grails.compiler.GrailsCompileStatic

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

    @Override
    String toString() { this.address }
}
