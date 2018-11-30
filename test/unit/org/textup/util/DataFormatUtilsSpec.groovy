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
class DataFormatUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    void "test json operations"() {
        given:
        Map seedData = [hello:[1, 2, 3], goodbye: "hello"]

        when: "from object to json string"
        String jsonString = DataFormatUtils.toJsonString(seedData)

        then:
        jsonString == '{"hello":[1,2,3],"goodbye":"hello"}'

        when: "from json string to obj"
        Object reconstructed = DataFormatUtils.jsonToObject(jsonString)

        then:
        reconstructed == seedData
    }
}
