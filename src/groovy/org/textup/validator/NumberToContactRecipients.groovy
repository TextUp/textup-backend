package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.util.*

@GrailsTypeChecked
@Validateable
class NumberToContactRecipients extends Recipients<String, Contact> {

    private List<String> _errorMessages = Collections.emptyList()

    static constraints = {
        recipients validator: { List<Contact> recips, NumberToContactRecipients obj ->
            if (obj._errorMessages) {
                ["contactErrors", obj._errorMessages]
            }
        }
    }

    @Override
    protected List<Contact> buildRecipientsFromIds(List<String> rawNums) {
        // validate to clear out old errors object, if present
        if (!phone) { return [] }
        List<String> msgs = []
        List<Contact> contacts = []
        for (String num in rawNums) {
            // ignore null values
            if (num == null) {
                continue
            }
            PhoneNumber pNum = new PhoneNumber(number:num)
            if (!pNum.validate()) {
                msgs += IOCUtils.resultFactory.failWithValidationErrors(pNum.errors).errorMessages
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
        _errorMessages = msgs
        contacts
    }
}
