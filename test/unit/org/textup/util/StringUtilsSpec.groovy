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
class StringUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    void "test appending strings while guaranteeing a max resulting length"() {
        expect:
        StringUtils.appendGuaranteeLength("hello", null, 1) == "h"
        StringUtils.appendGuaranteeLength(null, "yes", 1) == null
        StringUtils.appendGuaranteeLength("hello", "yes", -1) == "hello"
        StringUtils.appendGuaranteeLength("hello", "yes", 1) == "h"
        StringUtils.appendGuaranteeLength("hello", "yes", 6) == "helyes"
        StringUtils.appendGuaranteeLength("hello", "yes", 7) == "hellyes"
        StringUtils.appendGuaranteeLength("hello", "yes", 8) == "helloyes"
        StringUtils.appendGuaranteeLength("hello", "yes", 10) == "helloyes"
    }
}
