package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.mixin.web.ControllerUnitTestMixin
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.rest.*
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
@TestFor(TokenService)
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
class TokenServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creating TokenType.CALL_DIRECT_MESSAGE"() {
        given:
        String text1 = TestUtils.randString()
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildStaffPhone(s1)
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        VoiceLanguage lang1 = VoiceLanguage.values()[0]

        RecordItemType type = RecordItemType.CALL
        Recipients r1 = Recipients.tryCreate([ipr1], lang1, 10).payload
        TempRecordItem temp1 = TempRecordItem.tryCreate(text1, null, null).payload

        int tokBaseline = Token.count()

        when:
        Result res = service.tryBuildAndPersistCallToken(null, null, null)

        then:
        res.status == ResultStatus.OK
        res.payload == null
        Token.count() == tokBaseline

        when:
        res = service.tryBuildAndPersistCallToken(type, r1, temp1)

        then:
        temp1.supportsCall()
        res.status == ResultStatus.CREATED
        res.payload.type == TokenType.CALL_DIRECT_MESSAGE
        res.payload.maxNumAccess == 1
        res.payload.data[TokenType.PARAM_CDM_IDENT] == s1.name
        res.payload.data[TokenType.PARAM_CDM_MESSAGE] == text1
        res.payload.data[TokenType.PARAM_CDM_LANG] == lang1.toString()
        res.payload.data[TokenType.PARAM_CDM_MEDIA] == null
        Token.count() == tokBaseline  + 1
    }

    void "test redeeming TokenType.CALL_DIRECT_MESSAGE"() {
        given:
        String str1 = TestUtils.randString()
        MediaElement el1 = TestUtils.buildMediaElement()
        el1.sendVersion.type = MediaType.AUDIO_MP3
        MediaInfo mInfo1 = TestUtils.buildMediaInfo(el1)

        TypeMap data1 = TypeMap.create((TokenType.PARAM_CDM_IDENT): TestUtils.randString(),
            (TokenType.PARAM_CDM_MESSAGE): TestUtils.randString(),
            (TokenType.PARAM_CDM_LANG): VoiceLanguage.values()[0])
        TypeMap data2 = TypeMap.create((TokenType.PARAM_CDM_MEDIA): mInfo1.id,
            (TokenType.PARAM_CDM_IDENT): TestUtils.randString(),
            (TokenType.PARAM_CDM_MESSAGE): TestUtils.randString(),
            (TokenType.PARAM_CDM_LANG): VoiceLanguage.values()[0])

        Token mockToken = GroovyMock() { asBoolean() >> true }
        MockedMethod mustFindActiveForType = MockedMethod.create(Tokens, "mustFindActiveForType") {
            Result.createSuccess(mockToken)
        }
        MockedMethod directMessage = MockedMethod.create(CallTwiml, "directMessage") {
            Result.createSuccess(null)
        }

        when:
        Result res = service.buildDirectMessageCall(str1)

        then:
        mustFindActiveForType.latestArgs == [TokenType.CALL_DIRECT_MESSAGE, str1]
        1 * mockToken.setTimesAccessed(*_)
        mockToken.save() >> mockToken
        1 * mockToken.data >> data1
        directMessage.latestArgs == [data1[TokenType.PARAM_CDM_IDENT],
            data1[TokenType.PARAM_CDM_MESSAGE],
            data1[TokenType.PARAM_CDM_LANG],
            []]
        res.status == ResultStatus.OK

        when:
        res = service.buildDirectMessageCall(str1)

        then:
        mustFindActiveForType.latestArgs == [TokenType.CALL_DIRECT_MESSAGE, str1]
        1 * mockToken.setTimesAccessed(*_)
        mockToken.save() >> mockToken
        1 * mockToken.data >> data2
        directMessage.latestArgs == [data2[TokenType.PARAM_CDM_IDENT],
            data2[TokenType.PARAM_CDM_MESSAGE],
            data2[TokenType.PARAM_CDM_LANG],
            [el1.sendVersion.link]]

        cleanup:
        mustFindActiveForType?.restore()
        directMessage?.restore()
    }

    void "test creating TokenType.PASSWORD_RESET"() {
        given:
        Long staffId = TestUtils.randIntegerUpTo(88)
        int tokBaseline = Token.count()

        when:
        Result res = service.generatePasswordReset(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Token.count() == tokBaseline

        when:
        res = service.generatePasswordReset(staffId)

        then:
        res.status == ResultStatus.CREATED
        res.payload.type == TokenType.PASSWORD_RESET
        res.payload.maxNumAccess == 1
        Token.count() == tokBaseline + 1
    }

    void "test redeeming TokenType.PASSWORD_RESET"() {
        given:
        String str1 = TestUtils.randString()
        Staff s1 = TestUtils.buildStaff()

        Token mockToken = GroovyMock() { asBoolean() >> true }
        MockedMethod mustFindActiveForType = MockedMethod.create(Tokens, "mustFindActiveForType") {
            Result.createSuccess(mockToken)
        }

        when:
        Result res = service.findPasswordResetStaff(str1)

        then:
        mustFindActiveForType.latestArgs == [TokenType.PASSWORD_RESET, str1]
        1 * mockToken.setTimesAccessed(*_)
        mockToken.save() >> mockToken
        1 * mockToken.data >> TypeMap.create((TokenType.PARAM_PR_ID): s1.id )
        res.status == ResultStatus.OK
        res.payload == s1

        cleanup:
        mustFindActiveForType?.restore()
    }

    void "test creating TokenType.VERIFY_NUMBER"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        int tokBaseline = Token.count()

        when:
        Result res = service.generateVerifyNumber(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Token.count() == tokBaseline

        when:
        res = service.generateVerifyNumber(pNum1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.type == TokenType.VERIFY_NUMBER
        res.payload.maxNumAccess == null
        Token.count() == tokBaseline + 1
    }

    void "test redeeming TokenType.VERIFY_NUMBER"() {
        given:
        String str1 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Token mockToken = GroovyMock() { asBoolean() >> true }
        MockedMethod mustFindActiveForType = MockedMethod.create(Tokens, "mustFindActiveForType") {
            Result.createSuccess(mockToken)
        }

        when:
        Result res = service.findVerifyNumber(str1)

        then:
        mustFindActiveForType.latestArgs == [TokenType.VERIFY_NUMBER, str1]
        1 * mockToken.data >> TypeMap.create((TokenType.PARAM_VN_NUM): pNum1.number)
        res.status == ResultStatus.CREATED
        res.payload == pNum1

        cleanup:
        mustFindActiveForType?.restore()
    }

    void "test creating TokenType.NOTIFY_STAFF"() {
        given:
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy()
        op1.shouldSendPreviewLink = false
        OwnerPolicy op2 = TestUtils.buildOwnerPolicy()
        op2.shouldSendPreviewLink = true
        Notification notif1 = TestUtils.buildNotification()

        Token.withSession { it.flush() }
        int tokBaseline = Token.count()

        when:
        Result res = service.tryGeneratePreviewInfo(null, null)

        then:
        notThrown NullPointerException
        res.status == ResultStatus.OK
        res.payload == null
        Token.count() == tokBaseline

        when: "do not send preview link"
        res = service.tryGeneratePreviewInfo(op1, notif1)

        then:
        res.status == ResultStatus.OK
        res.payload == null
        Token.count() == tokBaseline

        when: "send preview link"
        res = service.tryGeneratePreviewInfo(op2, notif1)

        then:
        res.status == ResultStatus.OK
        res.payload.type == TokenType.NOTIFY_STAFF
        res.payload.maxNumAccess == ValidationUtils.MAX_NUM_ACCESS_NOTIFICATION_TOKEN
        res.payload.expires.isAfterNow()
        res.payload.data[TokenType.PARAM_NS_STAFF] == op2.staff.id
        res.payload.data[TokenType.PARAM_NS_ITEMS] == notif1.itemIds
        res.payload.data[TokenType.PARAM_NS_PHONE] == notif1.mutablePhone.id
        Token.count() == tokBaseline + 1
    }

    void "test redeeming TokenType.NOTIFY_STAFF"() {
        given:
        String str1 = TestUtils.randString()
        Long staffId = TestUtils.randIntegerUpTo(88)
        Notification notif1 = TestUtils.buildNotification()
        TypeMap data = TypeMap.create((TokenType.PARAM_NS_PHONE): notif1.mutablePhone.id,
            (TokenType.PARAM_NS_ITEMS): notif1.itemIds,
            (TokenType.PARAM_NS_STAFF): staffId)

        Token mockToken = GroovyMock() { asBoolean() >> true }
        MockedMethod mustFindActiveForType = MockedMethod.create(Tokens, "mustFindActiveForType") {
            Result.createSuccess(mockToken)
        }

        when:
        Result res = service.tryFindPreviewInfo(str1)

        then:
        mustFindActiveForType.latestArgs == [TokenType.NOTIFY_STAFF, str1]
        1 * mockToken.setTimesAccessed(*_)
        mockToken.save() >> mockToken
        1 * mockToken.data >> data
        res.status == ResultStatus.OK
        res.payload instanceof Tuple
        res.payload.first == staffId
        res.payload.second instanceof Notification
        res.payload.second.mutablePhone == notif1.mutablePhone
        res.payload.second.itemIds  == notif1.itemIds

        cleanup:
        mustFindActiveForType?.restore()
    }
}
