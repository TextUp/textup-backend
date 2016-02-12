package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode
class PasswordResetToken {

	DateTime expires = DateTime.now(DateTimeZone.UTC).plusHours(1)
    String token
    //id of the staff member who is requesting the reset
    Long toBeResetId

    static constraints = {
    	token unique:true
    }
    static mapping = {
        expires type:PersistentDateTime
    }

    boolean getIsExpired() {
        expires.isBeforeNow()
    }
    void expireNow() {
        this.expires = DateTime.now(DateTimeZone.UTC).minusMinutes(1)
    }
}
