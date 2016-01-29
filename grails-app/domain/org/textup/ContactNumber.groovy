package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode

@EqualsAndHashCode(callSuper=true)
class ContactNumber extends BasePhoneNumber {

	int preference = 0
    long ownerId

    static belongsTo = [contact:Contact]
    static constraints = {
        number shared:'phoneNumber', validator:{ val, obj ->
            //number must be unique within a contact
            if (obj.contactHasNumber(obj.contact, val)) { return ["duplicate", obj.contact?.name] }
        }
    }

    // Validation
    // ----------

    def beforeValidate() {
        //autoincrement the preference only on initial save
        if (this.id == null && this.contact && this.number) {
            ContactNumber.withNewSession { session ->
                session.flushMode = FlushMode.MANUAL
                try {
                    List<ContactNumber> cNums = ContactNumber.findAllByOwnerId(this.contact.id,
                        [max:1, sort:"preference", order:"desc"])
                    if (cNums) {
                        this.preference = cNums[0].preference + 1
                    }
                }
                finally { session.flushMode = FlushMode.AUTO }
            }
        }
    }
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

    // Property Access
    // ---------------

    void setContact(Contact c) {
        if (c) {
            this.contact = c
            this.ownerId = c.id
        }
    }
    static Map<String,List<Contact>> getContactsForPhoneAndNumbers(Phone p1,
        Collection<String> numbers) {
        List<ContactNumber> cNums = ContactNumber.createCriteria().list {
            contact { eq("phone", p1) }
            if (numbers) { "in"("number", numbers) }
            else { eq("number", null) }
            order("number")
        }
        HashSet<String> numsRemaining = new HashSet<String>(numbers)
        Map<String,List<Contact>> numAsStringToContacts = [:]
        cNums.each { ContactNumber cn ->
            numsRemaining.remove(cn.number)
            if (numAsStringToContacts.contains(cn.number)) {
                numAsStringToContacts[cn.number] << cn.contact
            }
            else {
                numAsStringToContacts[cn.number] = [cn.contact]
            }
        }
        numsRemaining.each { String numAsString ->
            numAsStringToContacts[numAsString] = []
        }
        numAsStringToContacts
    }
}
