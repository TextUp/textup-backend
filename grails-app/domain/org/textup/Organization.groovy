package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.interface.*
import org.textup.type.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
class Organization implements WithId, Saveable {

    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
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
    	name blank:false, validator:{ String val, Organization obj ->
            // TODO move to Organizations
    		//must have unique (name, location) combination
            Closure<Boolean> hasNameAndLocation = {
                List<Organization> orgs = Organization.createCriteria().list {
                    eq("name", val)
                    location {
                        eq("lat", obj.location?.lat)
                        eq("lon", obj.location?.lon)
                    }
                }
                // if there are organizations found, then check to see if these organizations
                // are DIFFERENT than the one we are trying to save. If there are organizations
                // that have the same name and location that aren't this same organization
                // then we have a duplicate
                !orgs.isEmpty() && orgs.any { Organization org -> org.id != obj.id }
            }
    		if (val && obj.location && Utils.<Boolean>doWithoutFlush(hasNameAndLocation)) {
                ["duplicate", obj.location?.address]
            }
    	}
        status blank: false, nullable: false
        timeout min: 0, max: ValidationUtils.MAX_LOCK_TIMEOUT_MILLIS
        // leave one character for the space for joining this suffix with an away message
        awayMessageSuffix nullable: true, blank: true, size: 1..(Constants.TEXT_LENGTH - 1)
        location cascadeValidation: true
    }

    static Result<Organization> create(String name, Location loc1) {
        DomainUtils.trySave(new Organization(name: name, location: loc1), ResultStatus.CREATED)
    }
}
