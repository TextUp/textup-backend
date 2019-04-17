package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.mixin.web.ControllerUnitTestMixin
import grails.test.runtime.*
import grails.validation.*
import java.lang.reflect.Proxy
import java.util.concurrent.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
@TestFor(OutgoingMessageService)
class OutgoingMessageServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test sending and storing"() {
        given:
        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()

        TempRecordItem temp1 = TestUtils.buildTempRecordItem()

        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        Record rec1 = TestUtils.buildRecord()
        RecordItem rItem1 = TestUtils.buildRecordItem(spr1.record)
        Map recIdToItems = [(spr1.record.id): [rItem1], (rec1.id): null]

        Token callToken = GroovyMock()
        service.outgoingMediaService = GroovyMock(OutgoingMediaService)

        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when:
        Result res = service.sendAndStore(spr1.toWrapper(), [rec1, spr1.record], temp1, recIdToItems, callToken)

        then: "errors are logged but otherwise ignored"
        1 * service.outgoingMediaService.trySend(spr1.shareSource.phone.number,
            spr1.shareSource.sortedNumbers,
            null,
            temp1.text,
            temp1.media,
            callToken) >> Result.createSuccess([tempRpt1])
        rItem1.receipts.size() == 1
        rItem1.receipts[0].apiId == tempRpt1.apiId
        stdErr.toString().contains("outgoingMessageService.itemsNotFound")
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        TestUtils.restoreAllStreams()
    }

    void "test finishing processing"() {
        given:
        RecordItemType type = RecordItemType.values()[0]
        RecordItem rItem1 = TestUtils.buildRecordItem()
        TempRecordItem temp1 = TestUtils.buildTempRecordItem()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        Record rec1 = TestUtils.buildRecord()

        DehydratedTempRecordItem dTemp1 = GroovyMock()
        Recipients recip1 = GroovyMock()
        DehydratedRecipients dr1 = GroovyMock()
        Token callToken = GroovyMock()
        service.tokenService = GroovyMock(TokenService)

        MockedMethod sendAndStore = MockedMethod.create(service, "sendAndStore") { Result.void() }

        when:
        service.finishProcessing(type, [rItem1.id], dr1, dTemp1)

        then:
        1 * dr1.tryRehydrate() >> Result.createSuccess(recip1)
        1 * dTemp1.tryRehydrate() >> Result.createSuccess(temp1)
        1 * service.tokenService.tryBuildAndPersistCallToken(type, recip1, temp1) >>
            Result.createSuccess(callToken)
        1 * recip1.eachIndividualWithRecords(_ as Closure) >> { args ->
            args[0].call(ipr1.toWrapper(), [rec1])
        }
        sendAndStore.latestArgs[0] == ipr1.toWrapper()
        sendAndStore.latestArgs[1] == [rec1]
        sendAndStore.latestArgs[2] == temp1
        sendAndStore.latestArgs[3].size() == 1
        sendAndStore.latestArgs[3][rItem1.record.id].size() == 1
        sendAndStore.latestArgs[3][rItem1.record.id][0] == rItem1
        sendAndStore.latestArgs[4] == callToken

        cleanup:
        sendAndStore?.restore()
    }

    void "test waiting for media before finishing processing"() {
        given:
        RecordItemType type = RecordItemType.values()[0]
        Collection itemIds = [TestUtils.randIntegerUpTo(88)]

        DehydratedTempRecordItem dTemp1 = GroovyMock()
        DehydratedRecipients dr1 = GroovyMock()
        Future fut1 = GroovyMock() { asBoolean() >> true }

        MockedMethod finishProcessing = MockedMethod.create(service, "finishProcessing") {
            Result.void()
        }
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when:
        service.waitForMedia(type, itemIds, dr1, dTemp1, null)

        then:
        finishProcessing.latestArgs == [type, itemIds, dr1, dTemp1]
        stdErr.size() == 0

        when:
        service.waitForMedia(type, itemIds, dr1, dTemp1, fut1)

        then:
        1 * fut1.get() >> Result.void()
        finishProcessing.latestArgs == [type, itemIds, dr1, dTemp1]
        stdErr.size() == 0

        when:
        service.waitForMedia(type, itemIds, dr1, dTemp1, fut1)

        then: "no log error because we may return a no-op future which has a null payload"
        1 * fut1.get() >> null
        finishProcessing.latestArgs == [type, itemIds, dr1, dTemp1]
        !stdErr.toString().contains("waitForMedia")

        cleanup:
        finishProcessing?.restore()
        TestUtils.restoreAllStreams()
    }

    void "test trying to send outgoing message overall"() {
        given:
        RecordItemType type = RecordItemType.values()[0]
        TempRecordItem temp1 = TestUtils.buildTempRecordItem()
        RecordItem rItem1 = TestUtils.buildRecordItem()
        Author author1 = TestUtils.buildAuthor()

        Future mediaFuture = GroovyMock()
        Recipients recip1 = GroovyMock()
        Record rec1 = GroovyMock()
        DehydratedRecipients dr1 = GroovyMock()
        DehydratedTempRecordItem dTemp1 = GroovyMock()
        Future fut1 = GroovyMock()
        ScheduledFuture fut2 = GroovyMock()
        service.threadService = GroovyMock(ThreadService)

        MockedMethod tryDehydrateRecipients = MockedMethod.create(DehydratedRecipients, "tryCreate") {
            Result.createSuccess(dr1)
        }
        MockedMethod tryDehydrateTempRecordItem = MockedMethod.create(DehydratedTempRecordItem, "tryCreate") {
            Result.createSuccess(dTemp1)
        }
        MockedMethod waitForMedia = MockedMethod.create(service, "waitForMedia")

        when:
        Result res = service.tryStart(null, null, null, null)

        then:
        notThrown NullPointerException
        res.status == ResultStatus.OK
        res.payload instanceof Tuple
        res.payload.first == []
        res.payload.second instanceof Proxy
        res.payload.second instanceof Future

        when:
        res = service.tryStart(type, recip1, temp1, author1, fut1)

        then:
        1 * recip1.eachRecord(_ as Closure) >> { args -> args[0].call(rec1) }
        1 * rec1.storeOutgoing(type, author1, temp1.text, temp1.media) >> Result.createSuccess(rItem1)
        1 * service.threadService.delay(*_) >> { args ->
            args[2].call()
            fut2
        }
        tryDehydrateRecipients.latestArgs == [recip1]
        tryDehydrateTempRecordItem.latestArgs == [temp1]
        waitForMedia.latestArgs == [type, [rItem1.id], dr1, dTemp1, fut1]
        res.status == ResultStatus.OK

        cleanup:
        tryDehydrateRecipients?.restore()
        tryDehydrateTempRecordItem?.restore()
        waitForMedia?.restore()
    }
}
