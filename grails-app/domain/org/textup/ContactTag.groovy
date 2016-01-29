package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(name="Tag", description="A tag for grouping contacts")
class ContactTag {

	Phone phone
    Record record

    @RestApiObjectField(description = "Name of this tag")
    String name

    @RestApiObjectField(
        description  = "Hex color code for labels for this tag",
        defaultValue = "#1BA5E0",
        mandatory    = false)
	String hexColor = "#1BA5E0"

    @RestApiObjectFields(params=[
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
    static hasMany = [members:Contact]
    static constraints = {
        name blank:false, nullable:false, validator:{ val, obj ->
            //for each phone, tags must have unique name
            if (val && obj.sameTagExists()) { ["duplicate"] }
        }
    	hexColor shared:"hexColor"
    }
    static mapping = {
        members lazy:false, cascade:"save-update"
    }
    static namedQueries = {
        forContact { Contact c1 ->
            members { idEq(c1?.id) }
        }
    }

    // Events
    // ------

    def beforeValidate() {
        if (!this.record) {
            this.record = new Record([:])
            this.record.save()
        }
    }

    // Validator
    // ---------

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

    // Property Access
    // ---------------

    void setRecord(Record r) {
        this.record = r
        this.record?.save()
    }

    // Record keeping
    // --------------

    Result<RecordText> addTextToRecord(Map params, Staff author) {
        this.record.addText(params, author)
    }

    // Members
    // -------

    List<Contact> getMembers(Collection statuses=[]) {
        if (statuses) {
            HashSet<ContactStatus> findStatuses =
                new HashSet<>(Helpers.toEnumList(ContactStatus, statuses))
            this.members.findAll { Contact c1 ->
                c1.status in findStatuses
            }
        }
        else { this.members }
    }
}
