package org.textup

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class TagMembership {

    ContactTag tag
	Contact contact 

    boolean hasUnsubscribed = false
    String subscriptionType

    static transients = ["subscribed"]
    static constraints = {
        contact validator:{ val, obj ->
            //contact's phone and tag's phone must be the same
            if (val.phone != obj.tag?.phone) {
                ["phoneMismatch", val.phone?.number, obj.tag?.phone?.number]
            }
        }
        subscriptionType blank:true, nullable:true, inList:[Constants.SUBSCRIPTION_TEXT, Call.SUBSCRIPTION_CALL]
    }
    static namedQueries = {
        forContactAndTagId { Contact c1, Long tagId ->
            tag { eq("id", tagId) }
            eq("contact", c1)
        }
        contactIdsForTag { ContactTag ct ->
            eq("tag", ct)
            projections { property("contact.id") }
        }
        allSubsForContactNumAndTeamPhone { String cNum, TeamPhone p1 ->
            eq("hasUnsubscribed", false)
            contact {
                numbers {
                    eq("number", cNum)
                }
                eq("phone", p1)
            }
        }
        textSubsForContactNumAndTeamPhone { String cNum, TeamPhone p1 ->
            allSubsForContactNumAndTeamPhone(cNum, p1)
            eq("subscriptionType", Constants.SUBSCRIPTION_TEXT)
        }
        textSubsForContactNumAndTeamPhone { String cNum, TeamPhone p1 ->
            allSubsForContactNumAndTeamPhone(cNum, p1)
            eq("subscriptionType", Constants.SUBSCRIPTION_CALL)
        }
    }

    /*
	Has many:
	*/
    
    ////////////////////
    // Helper methods //
    ////////////////////
    
    TagMembership subscribe(String subType) {
        this.hasUnsubscribed = false
        this.subscriptionType = subType
        this
    }
    TagMembership unsubscribe() {
        this.hasUnsubscribed = true
        this
    }

    /////////////////////
    // Property Access //
    /////////////////////
 
    boolean getSubscribed() { !this.hasUnsubscribed }
    void setSubscribed(boolean s) { this.hasUnsubscribed = !s }
}
