package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class RecipientsSpec extends Specification {

    void "test constraints"() {
        when: "empty object"
        Recipients<String, String> recip = new Recipients<>()

        then: "invalid"
        recip.validate() == false

        when: "set phone"
        recip.phone = new Phone()

        then: "valid"
        recip.validate() == true
    }

    void "test custom setter"() {
        given: "obj with both ids and recipients"
        Recipients<Integer, Integer> recip = new Recipients<>(phone: new Phone(),
            ids: [1, 2, 3], recipients: [4, 5, 6])
        assert recip.validate()

        when: "set null ids"
        recip.ids = null

        then: "both ids and recipients are cleared"
        Collections.emptyList() == recip.ids
        Collections.emptyList() == recip.recipients

        when: "set new ids"
        recip.ids = [2, 4, 6]

        then: "setting new ids attempts to build recipients from ids"
        [2, 4, 6] == recip.ids
        Collections.emptyList() == recip.recipients // from non-overridden method
    }

    void "test merging"() {
        given: "valid obj"
        Recipients<Integer, Integer> recip = new Recipients<>(phone: new Phone(),
            ids: [1, 2, 3], recipients: [4, 5, 6])
        assert recip.validate()

        when: "merge with another where both generics agree"
        Recipients<Integer, Integer> result = recip.mergeRecipients(new Recipients<Integer, Integer>(
            recipients: [2, 3, 4]))

        then: "ok"
        result != null

        when: "merge with another where only recipients generic agrees"
        result = recip.mergeRecipients(new Recipients<String, Integer>(
            recipients: [2, 3, 4]))

        then: "ok"
        result != null

        when: "merge with another where neither generic agrees"
        result = recip.mergeRecipients(new Recipients<String, String>(
            recipients: ["hello", "there"]))

        then: "still OK because generics type checking happens at compile time, not run time"
        result != null
    }

    // void "test building recipients from id"() {
    //     given:
    //     ContactRecipients recip = new ContactRecipients()

    //     expect:
    //     recip.buildRecipientsFromIds([]) == []
    //     recip.buildRecipientsFromIds([c1.id, c1_1.id]) == [c1, c1_1]
    //     recip.buildRecipientsFromIds([c1.id, c1_1.id, c2.id]) == [c1, c1_1, c2]
    // }

    // void "test constraints"() {
    //     when: "empty obj with no recipients"
    //     ContactRecipients recips = new ContactRecipients()

    //     then: "superclass constraints execute"
    //     recips.validate() == false
    //     recips.errors.getFieldErrorCount("phone") == 1

    //     when: "set phone"
    //     recips.phone = c1.phone

    //     then: "valid"
    //     recips.validate() == true

    //     when: "array of null ids"
    //     recips.ids = [null, null]

    //     then: "null values are ignored"
    //     recips.validate() == true

    //     when: "set ids with one foreign contact id + setter populates recipients"
    //     recips.ids = [c1.id, c2.id]

    //     then: "invalid foreign id"
    //     recips.validate() == false
    //     recips.errors.getFieldErrorCount("recipients") == 1

    //     when: "setting new ids + setter populates recipients"
    //     recips.ids = [c1.id, c1_1.id]

    //     then: "all valid"
    //     recips.validate() == true
    // }

    // void "test building recipients from id"() {
    //     given:
    //     ContactTagRecipients recips = new ContactTagRecipients()

    //     expect:
    //     recips.buildRecipientsFromIds([]) == []
    //     recips.buildRecipientsFromIds([tag1.id, tag1_1.id, tag2.id]) == [tag1, tag1_1, tag2]
    // }

    // void "test constraints"() {
    //     when: "empty obj with no recipients"
    //     ContactTagRecipients recips = new ContactTagRecipients()

    //     then: "superclass constraints execute"
    //     recips.validate() == false
    //     recips.errors.getFieldErrorCount("phone") == 1

    //     when: "with phone"
    //     recips.phone = p1

    //     then:
    //     recips.validate() == true

    //     when: "array of null ids"
    //     recips.ids = [null, null]

    //     then: "null values are ignored"
    //     recips.validate() == true

    //     when: "set ids with one foreign id + setter populates recipients"
    //     recips.ids = [tag1.id, tag1_1.id, tag2.id]

    //     then: "invalid foreign id"
    //     recips.validate() == false
    //     recips.errors.getFieldErrorCount("recipients") == 1

    //     when: "setting new ids + setter populates recipients"
    //     recips.ids = [tag1.id, tag1_1.id]

    //     then: "all valid"
    //     recips.validate() == true
    // }

    // void "test building recipients from string phone number"() {
    //     given: "empty obj"
    //     NumberToContactRecipients recips = new NumberToContactRecipients()

    //     when: "without phone or invalid"
    //     List<Contact> recipList = recips.buildRecipientsFromIds(["626 123 1234", "626 349 1029"])

    //     then: "short circuit, returns empty list"
    //     recipList == []

    //     when: "with phone and some invalid numbers"
    //     recips.phone = p1
    //     recipList = recips.buildRecipientsFromIds(["626 123 1234", "626 349", "291 291"])

    //     then: "obj has errors"
    //     recipList.size() == 1 // only build the one valid number
    //     recips.hasErrors() == false // save the error building for when we call validate

    //     when: "with phone and all valid numbers"
    //     recipList = recips.buildRecipientsFromIds(["626 123 1234", "626 349 2910"])

    //     then: "obj is valid"
    //     recipList.size() == 2
    //     recips.hasErrors() == false
    // }

    // void "test constraints"() {
    //     when: "empty obj with no recipients"
    //     NumberToContactRecipients recips = new NumberToContactRecipients()

    //     then: "superclass constraints execute"
    //     recips.validate() == false
    //     recips.errors.getFieldErrorCount("phone") == 1

    //     when: "with phone"
    //     recips.phone = p1

    //     then: "valid"
    //     recips.validate() == true

    //     when: "array of null ids"
    //     recips.ids = [null, null]

    //     then: "null values are ignored"
    //     recips.validate() == true

    //     when: "some invalid numbers"
    //     recips.ids = ["626 123 1234", "i am not a real phone number"]

    //     then:
    //     recips.validate() == false
    //     recips.errors.getFieldErrorCount("recipients") == 1

    //     when: "with some ids"
    //     recips.ids = ["626 123 1234", "626 349 2910"]

    //     then:
    //     recips.validate() == true
    //     recips.ids.size() == 2
    //     recips.recipients.size() == 2
    // }

    // void "test building recipients from id"() {
    //     when: "without phone"
    //     SharedContactRecipients recip = new SharedContactRecipients()

    //     then: "empty result"
    //     recip.buildRecipientsFromIds([1, 2, 3]) == Collections.emptyList()

    //     when: "with phone"
    //     recip.phone = p1

    //     then: "appropriate shared contacts from contact ids"
    //     recip.buildRecipientsFromIds([sc2.contactId]) == [sc2]
    // }

    // void "test constraints"() {
    //     when: "empty obj with no recipients"
    //     SharedContactRecipients recips = new SharedContactRecipients()

    //     then: "superclass constraints execute"
    //     recips.validate() == false
    //     recips.errors.getFieldErrorCount("phone") == 1

    //     when: "with phone"
    //     recips.phone = p1

    //     then: "valid"
    //     recips.validate() == true

    //     when: "array of null ids"
    //     recips.ids = [null, null]

    //     then: "null values are ignored"
    //     recips.validate() == true

    //     when: "set ids with one not shared contact id + setter populates recipients"
    //     recips.ids = [sc1.contactId, sc2.contactId]

    //     then: "invalid not shared id"
    //     recips.validate() == false
    //     recips.errors.getFieldErrorCount("recipients") == 1

    //     when: "setting new ids + setter populates recipients"
    //     recips.ids = [sc2.contactId]

    //     then: "all valid"
    //     recips.validate() == true
    // }
}
