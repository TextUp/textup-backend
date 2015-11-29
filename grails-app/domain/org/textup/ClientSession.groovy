package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import groovy.util.logging.Log4j

@Log4j
@EqualsAndHashCode
class ClientSession {

	TeamPhone teamPhone
	PhoneNumber number
    DateTime lastSentInstructions = DateTime.now(DateTimeZone.UTC)

    //id of the TeamContactTag that most recently sent this number a message
    Long mostRecentTagId

    static embedded = ["number"]
    static constraints = {
    	mostRecentTagId nullable:true
    }
    static mapping = {
        lastSentInstructions type:PersistentDateTime
    }
    static namedQueries = {
    	forTeamPhoneAndNumber { String teamPhoneNum, String num ->
    		eq("number.number", Helpers.cleanNumber(num))
    		teamPhone { eq("number.number", Helpers.cleanNumber(teamPhoneNum)) }
    	}
        forTeamPhoneAndNumber { TeamPhone p1, String num ->
            eq("number.number", Helpers.cleanNumber(num))
            eq("teamPhone", p1)
        }
    }

    ////////////////////
    // Helper Methods //
    ////////////////////

    static ClientSession findOrCreateForTeamPhoneAndNumber(TeamPhone p1, String num) {
    	ClientSession cs1 = ClientSession.forTeamPhoneAndNumber(p1, num).get()
        if (!cs1) {
            cs1 = new ClientSession(teamPhone:p1)
            cs1.numberAsString = num
            if (!cs1.save()) {
                log.error("ClientSession.findOrCreateForTeamPhoneAndNumber: could not create text session: ${cs1.errors}")
                cs1.discard()
                cs1 = null
            }
        }
        cs1
    }

    boolean shouldSendInstructions() {
        this.lastSentInstructions.isBefore(DateTime.now().withTimeAtStartOfDay())
    }
    void updateLastSentInstructions() {
        this.lastSentInstructions = DateTime.now(DateTimeZone.UTC)
    }

    boolean hasTextSubscriptions() {
        TagMembership.textSubsForContactNumAndTeamPhone(number.number, teamPhone).count() > 0
    }
    boolean hasCallSubscriptions() {
        TagMembership.callSubsForContactNumAndTeamPhone(number.number, teamPhone).count() > 0
    }
    boolean hasAnySubscriptions() {
        TagMembership.allSubsForContactNumAndTeamPhone(number.number, teamPhone).count() > 0
    }

    /////////////////////
    // Property Access //
    /////////////////////

    void setNumberAsString(String num) {
        if (this.number) {
            this.number.number = num
        }
        else {
            this.number = new PhoneNumber(number:num)
            this.number.save()
        }
    }
}
