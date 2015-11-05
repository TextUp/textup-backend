package org.textup

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class TagMembership {

    ContactTag tag
	Contact contact 

    boolean hasUnsubscribed = false

    static transients = ["subscribed"]
    static constraints = {
        contact validator:{ val, obj ->
            //contact's phone and tag's phone must be the same
            if (val.phone != obj.tag?.phone) {
                ["phoneMismatch", val.phone?.number, obj.tag?.phone?.number]
            }
        }
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
    }

    /*
	Has many:
	*/
    
    ////////////////////
    // Helper methods //
    ////////////////////
    
    TagMembership subscribe() {
        this.hasUnsubscribed = false
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
