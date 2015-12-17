package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(name="Tag", description="A tag for grouping contacts")
class ContactTag {

	Phone phone
    @RestApiObjectField(description = "Name of this tag")
    String name
    @RestApiObjectField(
        description  = "Hex color code for labels for this tag",
        defaultValue = "#1BA5E0",
        mandatory    = false)
	String hexColor = "#1BA5E0"

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "hasRecord",
            description    = "Whether this tag keeps a record of messages to its members",
            allowedType    = "Boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "lastRecordActivity",
            description    = "Date and time of the most recent communication if this tag keeps a record",
            allowedType    = "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "doTagActions",
            description       = "List of some actions to perform on contacts with relation to this tag",
            allowedType       = "List<[tagAction]>",
            useForCreation    = false,
            presentInResponse = false)
    ])
    static transients = []
    static constraints = {
        name blank:false, nullable:false, validator:{ val, obj ->
            //for each phone, tags must have unique name
            if (val && obj.sameTagExists()) { ["duplicate"] }
        }
    	hexColor blank:false, nullable:false, validator:{ val, obj ->
    		//String must be a valid hex color
            if (!(val ==~ /^#(\d|\w){3}/ || val ==~ /^#(\d|\w){6}/)) { ["invalidHex"] }
    	}
    }
    static namedQueries = {
        forStaff { thisStaff ->
            eq("phone", thisStaff.phone)
        }
        forTeam { thisTeam ->
            eq("phone", thisTeam.phone)
        }
    }

    /*
	Has many:
        TagMembership
	*/

    ////////////
    // Events //
    ////////////

    def beforeDelete() {
        ContactTag.withNewSession {
            TagMembership.where { tag == this }.deleteAll()
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    //Validation helper
    private boolean sameTagExists() {
        boolean duplicateTag = false
        ContactTag.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                ContactTag tag = ContactTag.findByPhoneAndName(phone, name)
                if (tag && tag.id != this.id) { duplicateTag = true }
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        duplicateTag
    }

    /////////////////////
    // Property Access //
    /////////////////////

    int countAllMembers() {
        TagMembership.countByTag(this)
    }
    List<TagMembership> getAllMembers() {
        TagMembership.findAllByTag(this)
    }
    List<TagMembership> getAllMembers(Map params) {
        TagMembership.findAllByTag(this, params)
    }
    int countSubscribers() {
        TagMembership.countByTagAndHasUnsubscribed(this, false)
    }
    List<TagMembership> getSubscribers() {
        TagMembership.findAllByTagAndHasUnsubscribed(this, false)
    }
    List<TagMembership> getSubscribers(Map params) {
        TagMembership.findAllByTagAndHasUnsubscribed(this, false, params)
    }
    int countNonsubscribers() {
        TagMembership.countByTagAndHasUnsubscribed(this, true)
    }
    List<TagMembership> getNonsubscribers() {
        TagMembership.findAllByTagAndHasUnsubscribed(this, true)
    }
    List<TagMembership> getNonsubscribers(Map params) {
        TagMembership.findAllByTagAndHasUnsubscribed(this, true, params)
    }
}
