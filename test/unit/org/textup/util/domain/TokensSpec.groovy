package org.textup.util.domain

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.web.ControllerUnitTestMixin
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

// [NOTE] may need to also mix in `ControllerUnitTestMixin` for as JSON casting to work
// see: https://stackoverflow.com/a/15485593

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
class TokensSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finder"() {
        given:
        Token tok1 = TestUtils.buildToken()
        Token tok2 = TestUtils.buildToken()
        tok2.expires = DateTime.now().minusDays(1)

        when: "nonexistent"
        Result res = Tokens.mustFindActiveForType(null, null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when: "expired"
        res = Tokens.mustFindActiveForType(tok2.type, tok2.token)

        then:
        res.status == ResultStatus.BAD_REQUEST

        when:
        res = Tokens.mustFindActiveForType(tok1.type, tok1.token)

        then:
        res.status == ResultStatus.OK
        res.payload == tok1
    }
}
