package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.restapidoc.annotation.*
import org.textup.rest.NotificationStatus
import org.textup.type.AuthorType
import org.textup.type.ContactStatus
import org.textup.validator.Author

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
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "doNotificationActions",
            description       = "List of actions that customize notification settings for specific staff members",
            allowedType       = "List<[notificationAction]>",
            useForCreation    = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "notificationStatuses",
            description    = "Whether or not a specified staff member will be notified of updates for this specific contact",
            allowedType    = "List<notificationStatus>",
            useForCreation = false)
    ])
    static transients = []
    static hasMany = [members:Contact]
    static constraints = {
        name blank:false, nullable:false, validator:{ String val, ContactTag obj ->
            //for each phone, tags must have unique name
            Closure<Boolean> sameTagExists = {
                ContactTag tag = ContactTag.findByPhoneAndNameAndIsDeleted(obj.phone, val, false)
                tag && tag.id != obj.id
            }
            if (val && Helpers.<Boolean>doWithoutFlush(sameTagExists)) { ["duplicate"] }
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

    // Static finders
    // --------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static List<ContactTag> findEveryByContactIds(Collection<Long> cIds) {
        if (!cIds) {
            return []
        }
        ContactTag
            .createCriteria()
            .listDistinct {
                members {
                    "in"("id", cIds)
                    eq("isDeleted", false)
                }
                eq("isDeleted", false)
            }
    }

    // Property access
    // ---------------

    void setRecord(Record r) {
        this.record = r
        this.record?.save()
    }
    List<NotificationStatus> getNotificationStatuses() {
        this.phone.owner.getNotificationStatusesForRecords([this.record.id])
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
