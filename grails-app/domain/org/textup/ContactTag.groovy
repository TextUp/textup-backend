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

    boolean isDeleted = false
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    PhoneRecord context
    String hexColor = Constants.DEFAULT_BRAND_COLOR
    String name

    static hasMany = [members: Contact]
    static mappedBy = [members: "none"] // members is a unidirectional association
    static mapping = {
        whenCreated type: PersistentDateTime
        members lazy: false, cascade: "save-update"
        context fetch: "join", cascade: "save-update"
    }
    static constraints = {
        name blank:false, nullable:false, validator:{ String val, ContactTag obj ->
            if (val && Utils.<Boolean>doWithoutFlush {
                ContactTag ct1 = ContactTag
                    .forPhoneIdAndOptions(obj.context?.phone?.id, val)
                    .list(max: 1)[0]
                ct1?.id != obj.id
            }) {
                ["duplicate"]
            }
        }
    	hexColor blank:false, nullable:false, validator:{ String val, ContactTag obj ->
            //String must be a valid hex color
            if (!(val ==~ /^#(\d|\w){3}/ || val ==~ /^#(\d|\w){6}/)) { ["invalidHex"] }
        }
        context cascadeValidation: true
    }

    // Methods
    // -------

    @Override
    Result<Record> tryGetRecord() {
        IOCUtils.resultFactory.success(this.record)
    }

    @Override
    Result<ReadOnlyRecord> tryGetReadOnlyRecord() {
        IOCUtils.resultFactory.success(this.record)
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { name }

    @Override
    String getPublicName() { name }

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
