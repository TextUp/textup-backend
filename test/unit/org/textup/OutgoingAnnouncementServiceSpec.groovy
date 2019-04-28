package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
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
@TestFor(OutgoingAnnouncementService)
@TestMixin(HibernateTestMixin)
class OutgoingAnnouncementServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test trying to store announcement in record items"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        Author author1 = TestUtils.buildAuthor()
        String msg1 = TestUtils.randString()
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()

        when:
        Result res = service.tryStoreForRecordItem(rec1, RecordItemType.TEXT, author1, msg1)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof RecordText
        res.payload.isAnnouncement
        res.payload.author == author1
        res.payload.contents == msg1
        res.payload.receipts == null

        when:
        res = service.tryStoreForRecordItem(rec1, RecordItemType.CALL, author1, msg1, tempRpt1)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof RecordCall
        res.payload.isAnnouncement
        res.payload.author == author1
        res.payload.noteContents == msg1
        res.payload.receipts.size() == 1
        res.payload.receipts[0].apiId == tempRpt1.apiId
    }

    void "test trying to store for records"() {
        given:
        RecordItemType type = RecordItemType.values()[0]
        String msg1 = TestUtils.randString()
        String errMsg1 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Author author1 = TestUtils.buildAuthor()
        Phone p1 = TestUtils.buildStaffPhone()
        IncomingSession is1 = TestUtils.buildSession()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()
        Map numToPhoneRecs = [(pNum1): [ipr1]]
        Map numToRpt = [(pNum1): tempRpt1]

        MockedMethod tryFindOrCreateNumToObjByPhoneAndNumbers = MockedMethod.create(IndividualPhoneRecords, "tryFindOrCreateNumToObjByPhoneAndNumbers") {
            Result.createSuccess(numToPhoneRecs)
        }
        MockedMethod tryStoreForRecordItem = MockedMethod.create(service, "tryStoreForRecordItem") {
            Result.createError([errMsg1], ResultStatus.BAD_REQUEST)
        }
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when:
        Result res = service.tryStoreForRecords(type, p1, author1, msg1, [is1], numToRpt)

        then:
        tryFindOrCreateNumToObjByPhoneAndNumbers.latestArgs == [p1, [is1.number], true, false]
        tryStoreForRecordItem.callCount == 1
        tryStoreForRecordItem.latestArgs == [ipr1.record, type, author1, msg1, tempRpt1]
        stdErr.toString().contains(errMsg1)
        res.status == ResultStatus.OK
        res.payload == [ipr1]

        cleanup:
        tryFindOrCreateNumToObjByPhoneAndNumbers?.restore()
        tryStoreForRecordItem?.restore()
        TestUtils.restoreAllStreams()
    }

    void "test trying to store announcement receipt"() {
        given:
        RecordItemType type = RecordItemType.TEXT
        Phone tp1 = TestUtils.buildTeamPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(tp1)
        IncomingSession is1 = TestUtils.buildSession(tp1)
        IncomingSession is2 = TestUtils.buildSession(tp1)

        int aRptBaseline = AnnouncementReceipt.count()

        when:
        Result res = service.tryStoreForAnnouncement(type, fa1, [is1, is2])

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof Collection
        res.payload.every { it.type == type }
        res.payload.every { it.announcement == fa1 }
        res.payload.find { it.session == is1 }
        res.payload.find { it.session == is2 }
        AnnouncementReceipt.count() == aRptBaseline + [is1, is2].size()
    }

    void "test trying to store overall"() {
        given:
        RecordItemType type = RecordItemType.values()[0]
        String msg1 = TestUtils.randString()

        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        Author author1 = TestUtils.buildAuthor()
        IncomingSession is1 = TestUtils.buildSession()
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()

        service.socketService = GroovyMock(SocketService)
        MockedMethod tryStoreForAnnouncement = MockedMethod.create(service, "tryStoreForAnnouncement") {
            Result.void()
        }
        MockedMethod tryStoreForRecords = MockedMethod.create(service, "tryStoreForRecords") {
            Result.createSuccess([ipr1])
        }

        when:
        Result res = service.tryStore(type, fa1, author1, msg1, [is1], [tempRpt1])

        then:
        tryStoreForAnnouncement.latestArgs == [type, fa1, [is1]]
        tryStoreForRecords.latestArgs == [type, fa1.phone, author1, msg1, [is1],
            [(tempRpt1.contactNumber): tempRpt1]]
        1 * service.socketService.sendIndividualWrappers([ipr1.toWrapper()])
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryStoreForAnnouncement?.restore()
        tryStoreForRecords?.restore()
    }

    void "test sending call announcement"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)
        Author author1 = TestUtils.buildAuthor()
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()
        TempRecordReceipt tempRpt2 = TestUtils.buildTempReceipt()

        IncomingSession is1 = TestUtils.buildSession(p1)
        is1.isSubscribedToCall = true
        IncomingSession is2 = TestUtils.buildSession(p1)
        is2.isSubscribedToCall = true
        IncomingSession.withSession { it.flush() }

        service.callService = GroovyMock(CallService)
        MockedMethod tryStore = MockedMethod.create(service, "tryStore") { Result.void() }

        when:
        Result res = service.sendCallAnnouncement(fa1, author1)

        then:
        1 * service.callService.start(p1.number, [is1.number], _, _) >> Result.createSuccess(tempRpt1)
        1 * service.callService.start(p1.number, [is2.number], _, _) >> Result.createSuccess(tempRpt2)
        tryStore.callCount == 1
        tryStore.latestArgs == [RecordItemType.CALL, fa1, author1, null, [is1, is2], [tempRpt1, tempRpt2]]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryStore?.restore()
    }

    void "test sending text announcement"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)
        Author author1 = TestUtils.buildAuthor()
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()
        TempRecordReceipt tempRpt2 = TestUtils.buildTempReceipt()

        IncomingSession is1 = TestUtils.buildSession(p1)
        is1.isSubscribedToText = true
        IncomingSession is2 = TestUtils.buildSession(p1)
        is2.isSubscribedToText = true
        IncomingSession.withSession { it.flush() }

        service.textService = GroovyMock(TextService)
        MockedMethod tryStore = MockedMethod.create(service, "tryStore") { Result.void() }

        when:
        Result res = service.sendTextAnnouncement(fa1, author1)

        then:
        1 * service.textService.send(p1.number, [is1.number], _, _) >> Result.createSuccess(tempRpt1)
        1 * service.textService.send(p1.number, [is2.number], _, _) >> Result.createSuccess(tempRpt2)
        tryStore.callCount == 1
        tryStore.latestArgs[0] == RecordItemType.TEXT
        tryStore.latestArgs[1] == fa1
        tryStore.latestArgs[2] == author1
        tryStore.latestArgs[3].contains(p1.buildName())
        tryStore.latestArgs[3].contains(fa1.message)
        tryStore.latestArgs[4] == [is1, is2]
        tryStore.latestArgs[5] == [tempRpt1, tempRpt2]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryStore?.restore()
    }

    void "test sending announcement overall"() {
        given:
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement()
        Author author1 = TestUtils.buildAuthor()

        MockedMethod sendTextAnnouncement = MockedMethod.create(service, "sendTextAnnouncement") {
            Result.void()
        }
        MockedMethod sendCallAnnouncement = MockedMethod.create(service, "sendCallAnnouncement") {
            Result.void()
        }

        when:
        Result res = service.send(fa1, author1)

        then:
        sendTextAnnouncement.latestArgs == [fa1, author1]
        sendCallAnnouncement.latestArgs == [fa1, author1]
        res.status == ResultStatus.OK
        res.payload == fa1

        cleanup:
        sendTextAnnouncement?.restore()
        sendCallAnnouncement?.restore()
    }
}
