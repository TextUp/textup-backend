package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@EqualsAndHashCode
class PasswordResetToken {

	DateTime expires = DateTime.now(DateTimeZone.UTC).plusHours(1)
    String token
    //id of the staff member who is requesting the reset
    long toBeResetId 

    static constraints = {
    	token unique:true
    }
    static mapping = {
        expires type:PersistentDateTime
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    boolean getIsExpired() { expires.isBeforeNow() }
    void expireNow() { expires = DateTime.now(DateTimeZone.UTC).minusMinutes(1) }

    /////////////////////
    // Property access //
    /////////////////////
}
