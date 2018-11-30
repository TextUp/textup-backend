package org.textup.validator

import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.Phone
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
}
