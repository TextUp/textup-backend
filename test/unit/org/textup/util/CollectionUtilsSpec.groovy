package org.textup.util

import org.textup.*
import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

// TODO check setup

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class CollectionUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    void "test take right"() {
        given: "a list"
        List data = [0, 1, 2, 3, 4]

        when: "list is null"
        List taken = CollectionUtils.takeRight(null, 2)

        then:
        taken == []

        when: "index is too small"
        taken = CollectionUtils.takeRight(data, -2)

        then:
        taken == []

        when: "index is too big"
        taken = CollectionUtils.takeRight(data, data.size() + 4)

        then:
        taken == []

        when: "take nothing"
        taken = CollectionUtils.takeRight(data, 0)

        then:
        taken == []

        when: "take all"
        taken = CollectionUtils.takeRight(data, data.size())

        then:
        taken == [0, 1, 2, 3, 4]

        when: "index is item in the middle of the list"
        taken = CollectionUtils.takeRight(data, data.size() - 1)

        then:
        taken == [1, 2, 3, 4]

        when: "index is item in the middle of the list"
        taken = CollectionUtils.takeRight(data, 3)

        then:
        taken == [2, 3, 4]
    }

    void "test in list ignoring case"() {
        expect:
        CollectionUtils.inListIgnoreCase("toBeFound", ["hello", "yes"]) == false
        CollectionUtils.inListIgnoreCase("toBeFound", ["tobefound"]) == true
        CollectionUtils.inListIgnoreCase("toBeFound", []) == false
        CollectionUtils.inListIgnoreCase(null, ["hello", "yes"]) == false
        CollectionUtils.inListIgnoreCase("toBeFound", null) == false
    }

    void "test join with different last"() {
        expect:
        CollectionUtils.joinWithDifferentLast([], ", ", " and ") == ""
        CollectionUtils.joinWithDifferentLast([1], ", ", " and ") == "1"
        CollectionUtils.joinWithDifferentLast([1, 2], ", ", " and ") == "1 and 2"
        CollectionUtils.joinWithDifferentLast([1, 2, 3], ", ", " and ") == "1, 2 and 3"
    }
}
