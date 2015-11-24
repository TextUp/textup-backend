package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.joda.time.DateTime
import static org.springframework.http.HttpStatus.*

@EqualsAndHashCode
class Phone {

    def grailsApplication
    def resultFactory
    def textService
    def authService

	PhoneNumber number

    static constraints = {
        number validator:{ pNum, obj ->
            //phone number must be unique for phones
            if (pNum && obj.existsWithSameNumber(pNum.number)) { ["duplicate"] }
        }
    }
    static transients = ["numberAsString"]
    static embedded = ["number"]
    static namedQueries = {
        forNumber { String num ->
            //embedded properties must be accessed with dot notation
            eq("number.number", num)
        }
    }

    /*
	Has many:
		Contact
		ContactTag
	*/

    ////////////
    // Events //
    ////////////

    def beforeDelete() {
        Phone.withNewSession {
            def tags = ContactTag.where { phone == this }
            def contacts = Contact.where { phone == this }
            //delete tag memberships, must come before
            //deleting ContactTag and Contact
            new DetachedCriteria(TagMembership).build {
                "in"("tag", tags.list())
            }.deleteAll()
            //must be before we delete our contacts FOR RECORD DELETION
            def associatedRecordIds = new DetachedCriteria(Contact).build {
                projections { property("record.id") }
                eq("phone", this)
            }.list()
            //delete contacts' numbers
            new DetachedCriteria(ContactNumber).build {
                "in"("contact", contacts.list())
            }.deleteAll()
            //delete contact and contact tags
            contacts.deleteAll()
            tags.deleteAll()
            //delete records associated with contacts, must
            //come after contacts are deleted
            new DetachedCriteria(Record).build {
                "in"("id", associatedRecordIds)
            }.deleteAll()
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    private boolean existsWithSameNumber(String num) {
        boolean hasDuplicate = false
        Phone.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                Phone ph = Phone.forNumber(num).get()
                if (ph && ph.id != this.id) { hasDuplicate = true }
            }
            catch (e) { hasDuplicate = true } //get throws exception if nonunique result
            finally { session.flushMode = FlushMode.AUTO }
        }
        hasDuplicate
    }

    /*
    Phone capabilities
     */

    Result<RecordResult> text(String message, List<String> numbers,
        List<Long> contactableIds, List<Long> tagIds) {
        //check message size
        Result msgSizeRes = textService.checkMessageSize(message)
        if (!msgSizeRes.success) return msgSizeRes
        //parse and validate each
        RecordResult recResult = new RecordResult()
        ParsedResult<PhoneNumber,String> parsedNums = Helpers.parseIntoPhoneNumbers(numbers)
        ParsedResult<Contactable,Long> parsedContactables = parseIntoContactables(contactableIds)
        ParsedResult<ContactTag,Long> parsedTags = parseIntoTags(tagIds)
        recResult.invalidNumbers += parsedNums.invalid
        recResult.invalidOrForbiddenContactableIds += parsedContactables.invalid
        recResult.invalidOrForbiddenTagIds += parsedTags.invalid
        //collect all contactables into one consensus list
        Set<Contactable> contactables = collectAllContactables(recResult,
            parsedNums.valid, parsedContactables.valid, parsedTags.valid)
        //check number of contactables
        Result numContRes = textService.checkNumRecipients(contactables.size())
        if (!numContRes.success) return numContRes
        //send the texts and return the result
        RecordResult textRecResult = sendTexts(message, contactables),
            overallRecResult = recResult.merge(textRecResult)
        if (overallRecResult.newItems) { resultFactory.success(overallRecResult) }
        else { resultFactory.failWithMessagesAndStatus(BAD_REQUEST, overallRecResult.errorMessages) }
    }
    Result<RecordResult> scheduleText(String message, DateTime sendAt,
        List<String> numbers, List<Long> contactableIds, List<Long> tagIds) {
        //TODO: implement me
        resultFactory.success(new RecordResult())
    }

    Result<RecordResult> call(String number) {
        Result res = resultFactory.failWithMessageAndStatus(BAD_REQUEST,
            "phone.error.invalidNumber", [number])
        ParsedResult<Contactable,String> parsedNums = parsePhoneNumberIntoContactables([number])
        if (parsedNums.valid) {
            Contactable c1 = parsedNums.valid[0]
            Result<RecordResult> cRes = c1.call([:])
            if (cRes.success) { res = resultFactory.success(cRes.payload) }
        }
        res
    }

    /**
     * Call a contact, if allowed
     * @param  contactId Id of the contact to call. Note this contact may be the id of a contact
     *                   that has been shared with you, NOT the shared contact id
     * @return           RecordResult
     */
    Result<RecordResult> call(Long contactId) {
        Result res = resultFactory.failWithMessageAndStatus(BAD_REQUEST,
            "phone.error.invalidContactId", [contactId])
        ParsedResult<Contactable,Long> parsedCs = parseIntoContactables([contactId])
        if (parsedCs.valid) {
            Contactable c1 = parsedCs.valid[0]
            Result<RecordResult> cRes = c1.call([:])
            if (cRes.success) { res = resultFactory.success(cRes.payload) }
        }
        res
    }

    /*
    Phone capabilities helper methods
     */

    protected ParsedResult<Contactable,Long> parseIntoContactables(List<Long> cIds) {
        ParsedResult<Long,Long> parsedIds = authService.parseContactIdsByPermission(cIds)
        List<Contact> contacts = Contact.getAll(parsedIds.valid)
        new ParsedResult(valid:contacts, invalid:parsedIds.invalid)
    }
    protected ParsedResult<ContactTag,Long> parseIntoTags(List<Long> tIds) {
        ParsedResult<Long,Long> parsedIds = authService.parseTagIdsByPermission(tIds)
        List<ContactTag> tags = ContactTag.getAll(parsedIds.valid)
        afterParsingTags(tags, parsedIds.invalid) //hook to override
        new ParsedResult(valid:tags, invalid:parsedIds.invalid)
    }
    protected Set<Contactable> collectAllContactables(RecordResult recResult,
        List<PhoneNumber> pNums, List<Contactable> contactables, List<ContactTag> tags) {
        HashSet<Contactable> all = new HashSet<>(contactables)
        //add tags
        tags.each { ContactTag tag -> all.addAll(tag.subscribers*.contact) }
        //parsed phone numbers and add
        ParsedResult<Contactable,String> parsedNums = parsePhoneNumberIntoContactables(pNums)
        recResult.invalidNumbers += parsedNums.invalid
        all.addAll(parsedNums.valid)
        all
    }
    protected ParsedResult<Contactable,String> parsePhoneNumberIntoContactables(List<PhoneNumber> pNums) {
        ParsedResult<Contactable,String> parsed = new ParsedResult<>()
        List<String> nums = pNums*.number
        //find the numbers that correspond to existing contacts
        List<ContactNumber> existingContactNumbers = ContactNumber.createCriteria().list {
            "in"("number", nums); contact { eq("phone", this) };
        }
        //add existing contacts to consensus
        existingContactNumbers.each { parsed.valid << it.contact }
        //create new contacts for new numbers, and then add these
        List<String> existingNums = existingContactNumbers*.number
        Helpers.parseFromList(existingNums, nums).invalid.each { String number ->
            Result<Contact> res = this.createContact([:], [number])
            if (res.success) { parsed.valid << res.payload }
            else { parsed.invalid << number }
        }
        parsed
    }
    protected RecordResult sendTexts(String message, Set<Contactable> contactables) {
        RecordResult recResult = new RecordResult()
        contactables.each { Contactable c ->
            Result<RecordResult> res = c.text(contents:message)
            afterSendTextTo(c, res) //hook to override
            if (res.success) { recResult.merge(res.payload) }
            else {
                recResult.invalidOrForbiddenContactableIds << c.id
                recResult.errorMessages += resultFactory.extractMessages(res)
            }
        }
        recResult
    }

    //Hooks to override, if needed
    protected void afterSendTextTo(Contactable c, Result res) { }
    protected void afterParsingTags(List<ContactTag> tags, List<Long> invalid) { }

    /*
    Tags
     */
    Result<ContactTag> createTag(Map params) {
        ContactTag tag = new ContactTag()
        tag.with {
            phone = this
            name = params.name
            if (params.hexColor) hexColor = params.hexColor
        }
        tag.phone = this
        if (tag.save()) { resultFactory.success(tag) }
        else { resultFactory.failWithValidationErrors(tag.errors) }
    }
    //deletes ContactTag and all associated TagMemberships
    Result deleteTag(String tagName) {
        ContactTag tag = ContactTag.findByPhoneAndName(this, tagName)
        if (tag) { deleteTag(tag) }
        else { resultFactory.failWithMessage("phone.error.tagNotFound", [tagName]) }
    }
    Result deleteTag(ContactTag tag) {
        if (tag.phone == this) {
            tag.delete()
            resultFactory.success()
        }
        else { resultFactory.failWithMessage("phone.error.tagOwnership", [tag.name, this.number]) }
    }

    /*
    Contacts -- are not allowed to delete contacts
     */
    Result<Contact> createContact(Map params=[:], List<String> numbers=[]) {
        Contact contact = new Contact([:])
        contact.properties = params
        contact.phone = this
        if (contact.save()) {
            //merge number has a dynamic finder that will flush
            Result prematureReturn = null
            Phone.withNewSession { session ->
                session.flushMode = FlushMode.MANUAL
                try {
                    int numbersLen = numbers.size()
                    for (int i = 0; i < numbersLen; i++) {
                        String num = numbers[i]
                        Result res = contact.mergeNumber(num, [preference:i])
                        if (!res.success) {
                            prematureReturn = resultFactory.failWithValidationErrors(res.payload)
                            return //return from withNewSession closure
                        }
                    }
                }
                finally { session.flushMode = FlushMode.AUTO }
            }
            prematureReturn ?: resultFactory.success(contact)
        }
        else { resultFactory.failWithValidationErrors(contact.errors) }
    }

    /////////////////////
    // Property Access //
    /////////////////////

    void setNumber(PhoneNumber pNum) {
        this.number = pNum
        this.number?.save()
    }
    void setNumberAsString(String num) {
        if (this.number) {
            this.number.number = num
        }
        else {
            this.number = new PhoneNumber(number:num)
        }
        this.number.save()
    }
    String getNumberAsString() { this.number?.number }

    List<ContactTag> getTags(Map params=[:]) {
        ContactTag.findAllByPhone(this, params)
    }

    //Optional specify 'status' corresponding to valid contact statuses
    List<Contactable> getContacts(Map params=[:]) {
        Contact.forPhoneAndStatuses(this, Helpers.toList(params.status)).list(params) ?: []
    }
    int countContacts(Map params=[:]) {
        Contact.forPhoneAndStatuses(this, Helpers.toList(params.status)).count() ?: 0
    }
}
