package org.textup

import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Log4j
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.validator.PhoneNumber
import org.textup.validator.BasePhoneNumber
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@Log4j
@EqualsAndHashCode
class IncomingSession {

    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    // initialize last sent in past so that we send instructions when the user
    // first tests in, if needed
    DateTime lastSentInstructions = DateTime.now(DateTimeZone.UTC).minusDays(2)
	Phone phone
    String numberAsString
    Boolean isSubscribedToText = false
    Boolean isSubscribedToCall = false

    static transients = ["number"]
    static mapping = {
        whenCreated type:PersistentDateTime
        lastSentInstructions type:PersistentDateTime
    }

    /*
    Has many:
        AnnouncementReceipt
     */

    // Instructions
    // ------------

    void updateLastSentInstructions() {
        this.lastSentInstructions = DateTime.now(DateTimeZone.UTC)
    }

    // Property Access
    // ---------------

    boolean getShouldSendInstructions() {
        this.lastSentInstructions.isBefore(DateTime.now().withTimeAtStartOfDay())
    }
    void setNumber(BasePhoneNumber pNum) {
        this.numberAsString = pNum?.number
    }
    PhoneNumber getNumber() {
        new PhoneNumber(number:this.numberAsString)
    }
}
