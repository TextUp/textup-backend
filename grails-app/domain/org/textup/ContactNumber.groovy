package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode

@EqualsAndHashCode(callSuper=true)
class ContactNumber extends PhoneNumber {

	int preference = 0
    long ownerId

    static constraints = {
        number validator:{ val, obj ->
            //number must be unique within a contact
            if (obj.contactHasNumber(obj.contact, val)) { return ["duplicate", obj.contact?.name] }
            //from superclass
            if (val?.size() != 10) { return ["format"] }
        }
    }
    static belongsTo = [contact:Contact]
    /*
	Has many:
	*/

    ////////////
    // Events //
    ////////////

    def beforeValidate() {
        //autoincrement the preference only on initial save
        if (this.id == null && this.contact && this.number) {
            ContactNumber.withNewSession { session ->
                session.flushMode = FlushMode.MANUAL
                try {
                    List<ContactNumber> cNums = ContactNumber.findAllByOwnerId(this.contact.id, [max:1, sort:"preference", order:"desc"])
                    if (cNums) {
                        this.preference = cNums[0].preference + 1
                    }
                }
                finally { session.flushMode = FlushMode.AUTO }
            }
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    private boolean contactHasNumber(Contact c, String num) {
        boolean hasDuplicate = false
        ContactNumber.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                ContactNumber cn = ContactNumber.findByOwnerIdAndNumber(c.id, num)
                if (cn && cn.id != this.id) { hasDuplicate = true }
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        hasDuplicate
    }

    /////////////////////
    // Property Access //
    /////////////////////

    void setContact(Contact c) {
        if (c) {
            this.contact = c
            this.ownerId = c.id
        }
    }
}
