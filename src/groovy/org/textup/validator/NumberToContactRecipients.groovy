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
        if (!phone) {
            return []
        }
        List<String> msgs = []
        List<Contact> contacts = []
        for (String num in rawNums) {
            // ignore null values
            if (num == null) {
                continue
            }
            PhoneNumber.createAndValidate(num)
                .then({ PhoneNumber pNum -> Contact.findEveryByNumbers(phone, [pNum], true) },
                    { Result failRes -> msgs += failRes.errorMessages })
                .then({ Map<PhoneNumber, List<Contact>> numberToContacts ->
                        numberToContacts.values().each { contacts.addAll(it) }
                    }, { Result failRes -> msgs += failRes.errorMessages })
        }
        _errorMessages = msgs
        contacts
    }
}
