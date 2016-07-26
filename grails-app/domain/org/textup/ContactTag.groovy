package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.restapidoc.annotation.*
import org.textup.types.AuthorType
import org.textup.types.ContactStatus
import org.textup.validator.Author
import grails.compiler.GrailsTypeChecked
import org.hibernate.Session

@GrailsTypeChecked
@EqualsAndHashCode
@RestApiObject(name="Tag", description="A tag for grouping contacts")
class ContactTag {

	Phone phone
    Record record
    boolean isDeleted = false

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
            description    = "Date and time of the most recent communication \
                if this tag keeps a record",
            allowedType    = "DateTime",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "doTagActions",
            description       = "List of some actions to perform on contacts \
                with relation to this tag",
            allowedType       = "List<[tagAction]>",
            useForCreation    = false,
            presentInResponse = false)
    ])
    static transients = []
    static hasMany = [members:Contact]
    static constraints = {
        name blank:false, nullable:false, validator:{ String val, ContactTag obj ->
            //for each phone, tags must have unique name
            if (val && obj.sameTagExists(val)) { ["duplicate"] }
        }
    	hexColor blank:false, nullable:false, validator:{ String val, ContactTag obj ->
            //String must be a valid hex color
            if (!(val ==~ /^#(\d|\w){3}/ || val ==~ /^#(\d|\w){6}/)) { ["invalidHex"] }
        }
    }
    static mappedBy = [members: "none"] // members is a unidirectional association
    static mapping = {
        members lazy:false, cascade:"save-update"
    }

    // Events
    // ------

    def beforeValidate() {
        if (!this.record) {
            this.record = new Record([:])
        }
    }

    // Validator
    // ---------

    protected boolean sameTagExists(String name) {
        boolean duplicateTag = false
        ContactTag.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                ContactTag tag = ContactTag.findByPhoneAndNameAndIsDeleted(phone,
                    name, false)
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

    Result<RecordText> addTextToRecord(Map params, Staff staff = null) {
        this.record.addText(params, staff?.toAuthor())
    }

    // Members
    // -------

    Collection<Contact> getMembersByStatus(Collection statuses=[]) {
        if (statuses) {
            HashSet<ContactStatus> findStatuses =
                new HashSet<>(Helpers.<ContactStatus>toEnumList(ContactStatus, statuses))
            this.members.findAll { Contact c1 ->
                c1.status in findStatuses
            }
        }
        else { this.members }
    }
}
