package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Organization implements WithId, CanSave<Organization>, ReadOnlyOrganization {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    DateTime whenCreated = JodaUtils.utcNow()
	String name
	Location location
    OrgStatus status = OrgStatus.PENDING
    int timeout = Constants.DEFAULT_LOCK_TIMEOUT_MILLIS
    String awayMessageSuffix = Constants.DEFAULT_AWAY_MESSAGE_SUFFIX

    static mapping = {
        whenCreated type: PersistentDateTime
        location cascade: "save-update"
    }
    static constraints = {
    	name validator: { String val, Organization obj ->
    		//must have unique name + location combination
    		if (val && obj.location && Utils.<Boolean>doWithoutFlush {
                    Organizations.buildForNameAndLatLng(val, obj.location.lat, obj.location.lng)
                        .build(CriteriaUtils.forNotIdIfPresent(obj.id))
                        .count() > 0
                }) {
                ["duplicate", obj.location.address]
            }
    	}
        timeout min: 0, max: ValidationUtils.MAX_LOCK_TIMEOUT_MILLIS
        // leave one character for the space for joining this suffix with an away message
        awayMessageSuffix nullable: true, blank: true, size: 1..(ValidationUtils.TEXT_BODY_LENGTH - 1)
        location cascadeValidation: true
    }

    static Result<Organization> tryCreate(String name, Location loc1) {
        DomainUtils.trySave(new Organization(name: name, location: loc1), ResultStatus.CREATED)
    }

    // Properties
    // ----------

    ReadOnlyLocation getReadOnlyLocation() { location }
}
