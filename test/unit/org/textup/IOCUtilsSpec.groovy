package org.textup

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
class IOCUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    void "testing getting webhook link"() {
        given:
        IOCUtils.metaClass."static".getLinkGenerator = { ->
            [link: { Map m -> m.toString() }] as LinkGenerator
        }
        String handle = TestUtils.randString()

        when:
        String link = IOCUtils.getWebhookLink(handle: handle)

        then: "test the stringified map"
        link.contains(handle)
        link.contains("handle")
        link.contains("publicRecord")
        link.contains("save")
        link.contains("v1")
    }

    void "test resolving message"() {
        given:
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }

        when: "from code"
        String code = TestUtils.randString()
        String msg = IOCUtils.getMessage(code, [1, 2, 3])

        then:
        msg == code

        when: "from resolvable object"
        Location emptyLoc1 = new Location()
        assert emptyLoc1.validate() == false
        msg = IOCUtils.getMessage(emptyLoc1.errors.allErrors[0])

        then:
        msg instanceof String
        msg != ""
    }
}
