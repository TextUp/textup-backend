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
class IncomingSession implements WithId, CanSave<IncomingSession> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    Boolean isSubscribedToCall = false
    Boolean isSubscribedToText = false
    DateTime lastSentInstructions = JodaUtils.utcNow().minusDays(2)
    DateTime whenCreated = JodaUtils.utcNow()
    Phone phone
    String numberAsString

    static transients = ["number"]
    static mapping = {
        whenCreated type: PersistentDateTime
        lastSentInstructions type: PersistentDateTime
    }
    static constraints = {
        numberAsString phoneNumber: true
    }

    static Result<IncomingSession> tryCreate(Phone p1, BasePhoneNumber bNum) {
        IncomingSession is1 = new IncomingSession(phone: p1, number: bNum)
        DomainUtils.trySave(is1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    void updateLastSentInstructions() {
        lastSentInstructions = JodaUtils.utcNow()
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
