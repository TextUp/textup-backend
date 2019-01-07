package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Contacts {

    static Result<Contact> create(Phone p1, List<? extends BasePhoneNumber> bNums = []) {
        Contact c1 = new Contact()
        c1.context = new PhoneRecord(phone: p1)
        // need to save contact before adding numbers so that the contact domain is assigned an
        // ID to be associated with the ContactNumbers to avoid a TransientObjectException
        if (c1.save()) {
            ResultGroup<ContactNumber> resGroup = new ResultGroup<>()
            bNums.unique().eachWithIndex { BasePhoneNumber bNum, int preference ->
                resGroup << c1.mergeNumber(bNum, preference)
            }
            if (resGroup.anyFailures) {
                IOCUtils.resultFactory.failWithGroup(resGroup)
            }
            else { IOCUtils.resultFactory.success(c1, ResultStatus.CREATED) }
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(c1.errors) }
    }

    static Result<Contact> update(Contact c1, Object name, Object note, Object lang) {
        if (name) {
            c1.name = TypeConversionUtils.to(String, name)
        }
        if (note) {
            c1.note = TypeConversionUtils.to(String, note)
        }
        if (lang) {
            c1.context.record.language = TypeConversionUtils.convertEnum(VoiceLanguage, lang)
        }
        if (c1.save()) {
            IOCUtils.resultFactory.success(c1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(c1.errors) }
    }

    static Result<Map<PhoneNumber, List<Contact>>> findEveryByNumbers(Phone p1,
        List<? extends BasePhoneNumber> bNums, boolean createIfAbsent) {

        // step 1: find all contact numbers that match the ones passed ine
        List<ContactNumber> cNums = ContactNumber.createCriteria().list {
            eq("owner.context.phone", p1)
            eq("owner.isDeleted", false)
            ne("owner.status", ContactStatus.BLOCKED)
            CriteriaUtils.inList(delegate, "number", bNums)
        } as List<ContactNumber>
        // step 2: group contacts by the passed-in phone numbers
        Map<PhoneNumber, List<Contact>> numberToContacts = [:].withDefault { [] as List<Contact> }
        cNums.each { ContactNumber cNum -> numberToContacts[cNum] << cNum.owner }
        // step 3: if allowed, create new contacts for any phone numbers without any contacts
        if (createIfAbsent) {
            Contact.createContactIfNone(p1, numberToContacts)
        }
        else { IOCUtils.resultFactory.success(numberToContacts) }
    }

    // Helpers
    // -------

    protected static Result<Map<PhoneNumber, List<Contact>>> createContactIfNone(Phone p1,
        Map<PhoneNumber, List<Contact>> numberToContacts) {

        ResultGroup<Contact> resGroup = new ResultGroup<>()
        numberToContacts.each { PhoneNumber pNum, List<Contact> contacts ->
            if (contacts.isEmpty()) {
                resGroup << Contact.create(p1, [pNum]).then { Contact c1 ->
                    contacts << c1
                    IOCUtils.resultFactory.success(c1)
                }
            }
        }
        if (resGroup.anyFailures) {
            IOCUtils.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success(numberToContacts) }
    }
}
