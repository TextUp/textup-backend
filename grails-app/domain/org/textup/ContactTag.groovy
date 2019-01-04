package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.rest.NotificationStatus
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@EqualsAndHashCode
class ContactTag implements WithId, WithRecord, WithName {

    // TODO
    // Phone phone
    // Record record

    boolean isDeleted = false
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    PhoneRecord context
    String hexColor = Constants.DEFAULT_BRAND_COLOR
    String name

    static hasMany = [members:Contact]
    static mappedBy = [members: "none"] // members is a unidirectional association
    static mapping = {
        whenCreated type: PersistentDateTime
        members lazy: false, cascade: "save-update"
        context fetch: "join", cascade: "save-update"
    }
    static constraints = {
        name blank:false, nullable:false, validator:{ String val, ContactTag obj ->
            //for each phone, tags must have unique name
            Closure<Boolean> sameTagExists = {
                ContactTag tag = ContactTag.findByPhoneAndNameAndIsDeleted(obj.phone, val, false)
                tag && tag.id != obj.id
            }
            if (val && Utils.<Boolean>doWithoutFlush(sameTagExists)) { ["duplicate"] }
        }
    	hexColor blank:false, nullable:false, validator:{ String val, ContactTag obj ->
            //String must be a valid hex color
            if (!(val ==~ /^#(\d|\w){3}/ || val ==~ /^#(\d|\w){6}/)) { ["invalidHex"] }
        }
        context cascadeValidation: true
    }

    // Static finders
    // --------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static List<ContactTag> findEveryByContactIds(Collection<Long> cIds) {
        if (!cIds) {
            return []
        }
        ContactTag.createCriteria().listDistinct {
            members {
                CriteriaUtils.inList(delegate, "id", cIds)
                eq("isDeleted", false)
            }
            eq("isDeleted", false)
        }
    }

    static Result<ContactTag> create(Phone p1, String name) {
        ContactTag ct1 = new ContactTag(name: name)
        c1.context = new PhoneRecord(phone: p1)
        if (ct1.save()) {
            IOCUtils.resultFactory.success(ct1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(ct1.errors) }
    }

    // Property access
    // ---------------

    Result<Record> tryGetRecord() {
        IOCUtils.resultFactory.success(this.record)
    }
    Result<ReadOnlyRecord> tryGetReadOnlyRecord() {
        IOCUtils.resultFactory.success(this.record)
    }

    // Members
    // -------

    Collection<Contact> getMembersByStatus(Collection statuses=[]) {
        if (statuses) {
            HashSet<ContactStatus> findStatuses =
                new HashSet<>(TypeConversionUtils.toEnumList(ContactStatus, statuses))
            this.members.findAll { Contact c1 ->
                c1.status in findStatuses
            }
        }
        else { this.members }
    }
}
