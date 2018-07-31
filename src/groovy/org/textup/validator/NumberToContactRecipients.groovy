package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*

@GrailsTypeChecked
@Validateable
class NumberToContactRecipients extends Recipients<String, Contact> {

    @Override
    protected List<Contact> buildRecipientsFromIds(List<String> rawNums) {
        // validate to clear out old errors object, if present
        if (!validate() || !phone) { return [] }
        List<String> msgs = []
        List<Contact> contacts = []
        for (String num in rawNums) {
            PhoneNumber pNum = new PhoneNumber(number:num)
            // ignore invalid phone numbers
            if (!pNum.validate()) {
                msgs += Helpers.resultFactory.failWithValidationErrors(pNum.errors).errorMessages
                continue
            }
            // add existing contacts to recipients
            List<Contact> existing = Contact.listForPhoneAndNum(phone, pNum)
            if (existing) { contacts += existing }
            else { // if no existing, create new contact with this number
                Result<Contact> res = phone.createContact([:], [pNum.number])
                if (res.success) {
                    contacts << res.payload
                }
                else { msgs += res.errorMessages }
            }
        }
        if (msgs) {
            this.errors.rejectValue("recipients", "numberToContactRecipients.recipients.contactErrors",
                msgs as Object[], "Something went wrong when looking up or creating contacts from phone numbers")
        }
        contacts
    }
}
