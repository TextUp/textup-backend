package org.textup.rest

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class TextTwimlSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test building text strings"() {
        when:
        String msg1 = TestUtils.randString()
        String msg2 = TestUtils.randString()
        Result res = TextTwiml.build([msg2, msg1])

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Message(msg2)
                Message(msg1)
            }
        }
    }

    void "test errors"() {
        when: "invalid number for text"
        Result res = TextTwiml.invalid()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response { Message("twiml.invalidNumber") }
        }

        when: "not found for text"
        res = TextTwiml.notFound()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response { Message("twiml.notFound") }
        }
    }

    void "test texts"() {
        when: "subscribed"
        Result res = TextTwiml.subscribed()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response { Message("textTwiml.subscribed") }
        }

        when: "unsubscribed"
        res = TextTwiml.unsubscribed()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response { Message("textTwiml.unsubscribed") }
        }

        when: "blocked"
        res = TextTwiml.blocked()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response { Message("textTwiml.blocked") }
        }
    }
}
