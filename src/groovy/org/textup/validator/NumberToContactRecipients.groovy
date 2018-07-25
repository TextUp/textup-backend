package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import org.textup.*

@GrailsCompileStatic
@Validateable
class NumberToContactRecipients extends Recipients<String, Contact> {

    private List<Contact> recipients = Collections.emptyList()

    // Events
    // ------

    def beforeValidate() {
        if (ids && phone && !recipients) {
            List<String> msgs = []
            List<Contact> contacts = []
            Helpers.<Void>doWithoutFlush {
                for (Object num in ids) {
                    PhoneNumber pNum = new PhoneNumber(number:num as String)
                    if (!pNum.validate()) { // ignore invalid phone numbers
                        continue
                    }
                    List<Contact> existing = Contact.listForPhoneAndNum(phone, pNum)
                    // add existing contacts to recipients
                    if (existing) { contacts += existing }
                    else { // if no existing, create new contact with this number
                        Result<Contact> res = phone.createContact([:], [pNum.number])
                        if (res.success) {
                            contacts << res.payload
                        }
                        else { msgs += res.errorMessages }
                    }
                }
            }
            if (msgs) {
                this.errors.rejectValue("recipients", "numberToContactRecipients.recipients.contactErrors",
                    msgs as Object[], "Something went wrong when looking up or creating contacts from phone numbers")
            }
            recipients = contacts
        }
    }

    // Property access
    // ---------------

    List<PhoneNumber> getRecipients() { this.recipients }
}
