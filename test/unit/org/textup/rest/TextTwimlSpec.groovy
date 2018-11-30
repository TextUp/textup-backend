package org.textup.rest

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
 RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
 Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
 AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class TextTwimlSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    void "test building text strings"() {
        when:
        String msg1 = "hello"
        String msg2 = "there"
        Result<Closure> res = TextTwiml.build([msg2, msg1])

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Message(msg2)
                Message(msg1)
            }
        })
    }

    void "test errors"() {
        when: "invalid number for text"
        Result<Closure> res = TextTwiml.invalid()

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response { Message("twimlBuilder.invalidNumber") }
        })

        when: "not found for text"
        res = TextTwiml.notFound()

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response { Message("twimlBuilder.notFound") }
        })
    }

    void "test texts"() {
        when: "subscribed"
        Result<Closure> res = TextTwiml.subscribed()

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response { Message("twimlBuilder.text.subscribed") }
        })

        when: "unsubscribed"
        res = TextTwiml.unsubscribed()

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response { Message("twimlBuilder.text.unsubscribed") }
        })

        when: "blocked"
        res = TextTwiml.blocked()

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response { Message("twimlBuilder.text.blocked") }
        })
    }

    void "test seeing announcements"() {
        when: "announcements, no params"
        Result<Closure> res = TextTwiml.seeAnnouncements(null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "announcements empty list"
        res = TextTwiml.seeAnnouncements([])

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response { Message("twimlBuilder.noAnnouncements") }
        })

        when: "announcements, valid"
        Collection<FeaturedAnnouncement> announces = [[
            whenCreated: DateTime.now(),
            owner: p1,
            message: "hello1"
        ] as FeaturedAnnouncement, [
            whenCreated: DateTime.now(),
            owner: p1,
            message: "hello2"
        ] as FeaturedAnnouncement]
        res = TextTwiml.seeAnnouncements(announces)

        then:
        res.success == true
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Message("twimlBuilder.announcement")
                Message("twimlBuilder.announcement")
            }
        })
    }
}
