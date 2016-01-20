package org.textup

import groovy.transform.EqualsAndHashCode
import grails.gorm.DetachedCriteria
import org.hibernate.FlushMode

@EqualsAndHashCode(callSuper=true)
class StaffPhone extends Phone {

    //grailsApplication from superclass
    //resultFactory from superclass

    Long ownerId

    //NOTE: authorId on the individual record items
    //correspond to Staff id

    static constraints = {
        ownerId validator:{ val, obj ->
            if (!obj.isOwnerStaff(val)) { ["invalid"] }
        }
    }
    static namedQueries = {
        forStaffNumber { TransientPhoneNumber num ->
            //embedded properties must be accessed with dot notation
            eq("number.number", num?.number)
        }
    }

    /*
	Has many:
		Contact (from superclass)
		ContactTag (from superclass)
		SharedContact
	*/

    ////////////
    // Events //
    ////////////

    def beforeDelete() {
        StaffPhone.withNewSession {
            def tags = ContactTag.where { phone == this }
            def contacts = Contact.where { phone == this }
            //delete tag memberships, must come before
            //deleting ContactTag and Contact
            new DetachedCriteria(TagMembership).build {
                def res = tags.list()
                if (res) { "in"("tag", res) }
                else { eq("tag", null) }
            }.deleteAll()
            //must be before we delete our contacts FOR RECORD DELETION
            def associatedRecordIds = new DetachedCriteria(Contact).build {
                projections { property("record.id") }
                eq("phone", this)
            }.list()
            //delete contacts' numbers
            new DetachedCriteria(ContactNumber).build {
                def res = contacts.list()
                if (res) { "in"("contact", res) }
                else { eq("contact", null) }
            }.deleteAll()
            //delete shared contacts
            SharedContact.where { sharedBy == this || sharedWith == this }.deleteAll()
            //delete contact and contact tags
            contacts.deleteAll()
            tags.deleteAll()
            //delete records associated with contacts, must
            //come after contacts are deleted
            new DetachedCriteria(Record).build {
                if (associatedRecordIds) { "in"("id", associatedRecordIds) }
                else { eq("id", null) }
            }.deleteAll()
        }
    }


    ////////////////////
    // Helper methods //
    ////////////////////

    private boolean isOwnerStaff(Long id) {
        if (id == null) { return false }
        boolean isStaff = false
        StaffPhone.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                isStaff = Staff.exists(id)
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        isStaff
    }

    /*
    Sharing operations
     */
    Result<SharedContact> shareContact(Contact contact, StaffPhone shareWith, String permission) {
        if (validateContact(contact)) {
            if (isSameTeam(shareWith)) {
                //check to see that there isn't already an active shared contact
                int numNotExpired = SharedContact.nonexpiredFor(contact, this, shareWith).count()
                SharedContact sc
                if (numNotExpired == 0) {
                    sc = new SharedContact(contact:contact, sharedBy:this,
                        sharedWith:shareWith, permission:permission)
                }
                else {
                    List<SharedContact> alreadyShared = SharedContact.nonexpiredFor(contact, this, shareWith).list(max:1)
                    sc = alreadyShared[0]
                    sc.permission = permission
                }
                if (sc.save()) { resultFactory.success(sc) }
                else { resultFactory.failWithValidationErrors(sc.errors) }
            }
            else { resultFactory.failWithMessage("staffPhone.error.differentTeams") }
        }
        else { resultFactory.failWithMessage("staffPhone.error.allNotShared", [contact?.name]) }
    }
    Result<List<SharedContact>> stopSharingWith(StaffPhone sharedWith) {
        List<SharedContact> allSharedBtwn = SharedContact.allNonexpiredBetween(this, sharedWith).list()
        allSharedBtwn.each { SharedContact sc -> sc.stopSharing() }
        resultFactory.success(allSharedBtwn)
    }
    Result<SharedContact> stopSharingContactWith(Contact c1, StaffPhone sharedWith) {
        SharedContact sc1 = SharedContact.findByContactAndSharedWith(c1, sharedWith)
        if (sc1) {
            sc1.stopSharing()
            resultFactory.success(sc1)
        }
        else { resultFactory.failWithMessage("staffPhone.error.allNotShared", [c1?.name]) }
    }
    Result<List<SharedContact>> stopSharing(Contact contact) {
        if (validateContact(contact)) {
            int numNotExpired = SharedContact.allNonexpiredFor(contact, this).count()
            if (numNotExpired != 0) {
                List<SharedContact> allShared = SharedContact.allNonexpiredFor(contact, this).list()
                allShared.each { SharedContact sc -> sc.stopSharing() }
                resultFactory.success(allShared)
            }
            else {
                resultFactory.failWithMessage("staffPhone.error.allNotShared", [contact?.name])
            }
        }
        else { resultFactory.failWithMessage("staffPhone.error.contactNotMine", [contact?.name]) }
    }
    private boolean isSameTeam(StaffPhone sWith) {
        TeamMembership.staffIdsOnSameTeamAs(this.ownerId).list().contains(sWith.ownerId)
    }
    private boolean validateContact(Contact contact) {
        contact && contact.id != null && contact.phone == this
    }

    /*
    Texting shared contacts
     */
    @Override
    protected ParsedResult<Contactable,Long> parseIntoContactables(List<Long> cIds) {
        ParsedResult<Contactable,Long> superRes = super.parseIntoContactables(cIds)
        //see if any of the invalid contact ids are actually contacts that are shared with us
        ParsedResult<SharedContact,Long> parsed = authService.parseIntoSharedContactsByPermission(superRes.invalid)
        new ParsedResult(valid:(superRes.valid + parsed.valid), invalid:parsed.invalid)
    }

    /////////////////////
    // Property Access //
    /////////////////////

    @Override
    //Optional specify 'status' corresponding to valid contact statuses
    List<Contactable> getContacts(Map params=[:]) {
        List<Contactable> contactables = Contact.forStaffPhoneAndStatuses(this,
            Helpers.toList(params.status)).list(params)
        //identify those contacts that are shared with me and their order in the results
        Map<Long,Integer> sharedContactIdToIndices = [:]
        contactables.eachWithIndex { Contact contact, int i ->
            if (contact.phone != this) sharedContactIdToIndices[contact.id] = i
        }
        //retrieve the corresponding SharedContact instance
        List<SharedContact> sharedContacts = SharedContact.sharedWithForContactIds(this,
            sharedContactIdToIndices.keySet()).list()
        if (sharedContacts.size() != sharedContactIdToIndices.size()) {
            log.error("""StaffPhone.getContacts with params: $params. Not all shared
                contacts found. Expected: ${sharedContactIdToIndices.keySet()}.
                Found: ${sharedContacts}""")
        }
        //store retrieved SharedContact list as a HashMap for faster retrieval
        Map contactIdToSharedContact = sharedContacts.collectEntries { [(it.contact.id):it] }
        //replace the shared with me Contacts with their SharedContact counterpart
        List<Integer> indicesToBeRemoved = []
        sharedContactIdToIndices.each { contactId, index ->
            if (contactIdToSharedContact.containsKey(contactId)) {
                contactables[index] = contactIdToSharedContact[contactId]
            }
            //removing now would mess up our stored indices
            else { indicesToBeRemoved << index }
        }
        //if some of the shared contacts could not be found, for example, if the
        //shared contact was expired while this operation is in progress, remove this
        //shared contact result from our result list
        indicesToBeRemoved.each { int i -> contactables.remove(i) }

        contactables
    }
    @Override
    int countContacts(Map params=[:]) {
        Contact.forStaffPhoneAndStatuses(this, Helpers.toList(params.status)).count()
    }

    int countSharedWithMe() {
        SharedContact.sharedWithMe(this).count()
    }
    List<SharedContact> getSharedWithMe(Map params=[:]) {
        SharedContact.sharedWithMe(this).list(params) ?: []
    }
    int countSharedByMe() {
        SharedContact.sharedByMe(this).count()
    }
    List<Contact> getSharedByMe(Map params=[:]) {
        SharedContact.sharedByMe(this).list(params) ?: []
    }
}
