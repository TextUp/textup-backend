package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode
class IncomingSession implements WithId, Saveable<IncomingSession> {

    Boolean isSubscribedToCall = false
    Boolean isSubscribedToText = false
    DateTime lastSentInstructions = DateTimeUtils.now().minusDays(2)
    DateTime whenCreated = DateTimeUtils.now()
    Phone phone
    String numberAsString

    static transients = ["number"]
    static mapping = {
        whenCreated type: PersistentDateTime
        lastSentInstructions type: PersistentDateTime
    }

    // Methods
    // -------

    void updateLastSentInstructions() {
        lastSentInstructions = DateTimeUtils.now()
    }

    Author toAuthor() {
        new Author(id: id, type: AuthorType.SESSION, name: numberAsString)
    }

    // Properties
    // ----------

    boolean getShouldSendInstructions() {
        lastSentInstructions.isBefore(DateTime.now().withTimeAtStartOfDay())
    }

    void setNumber(BasePhoneNumber pNum) {
        numberAsString = pNum?.number
    }

    PhoneNumber getNumber() {
        PhoneNumber.create(numberAsString)
    }
}
