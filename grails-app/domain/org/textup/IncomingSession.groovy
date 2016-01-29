package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import groovy.util.logging.Log4j

@Log4j
@EqualsAndHashCode
class IncomingSession {

    DateTime dateCreated = DateTime.now(DateTimeZone.UTC)
    DateTime lastSentInstructions = DateTime.now(DateTimeZone.UTC)
	Phone phone
    String numberAsString
    boolean isSubscribedToText = false
    boolean isSubscribedToCall = false

    static transients = ["number"]
    static mapping = {
        autoTimestamp false
        dateCreated type:PersistentDateTime
        lastSentInstructions type:PersistentDateTime
    }
    static namedQueries = {
        subscribedToText { Phone p1 ->
            eq('phone', p1)
            eq('isSubscribedToText', true)
        }
        subscribedToCall { Phone p1 ->
            eq('phone', p1)
            eq('isSubscribedToCall', true)
        }
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
    void setNumber(PhoneNumber pNum) {
        this.numberAsString = pNum?.number
    }
    PhoneNumber getNumber() {
        new PhoneNumber(number:this.numberAsString)
    }
}
