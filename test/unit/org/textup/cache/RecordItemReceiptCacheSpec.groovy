package org.textup.cache

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import grails.plugin.cache.GrailsValueWrapper
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import org.springframework.cache.Cache
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class RecordItemReceiptCacheSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    RecordItemReceiptCache rCache

    def setup() {
        TestUtils.standardMockSetup()

        rCache = new RecordItemReceiptCache()
    }

    @DirtiesRuntime
    void "test finding all receipts by api id"() {
        given:
        RecordItemReceipt mockRpt = GroovyMock()
        RecordItemReceipt.metaClass."static".findAllByApiId = { String apiId -> [mockRpt] }
        String apiId = TestUtils.randString()

        when:
        List rptList = rCache.findEveryReceiptInfoByApiId(apiId)

        then:
        1 * mockRpt.toInfo() >> GroovyMock(RecordItemReceiptCacheInfo)
        rptList.size() == 1
    }

    void "test updating receipts no receipts"() {
        given:
        ReceiptStatus newStatus = ReceiptStatus.SUCCESS
        Integer newDuration = TestUtils.randIntegerUpTo(100, true)
        String apiId = TestUtils.randString()

        expect:
        rCache.updateReceipts(apiId, null, newStatus, newDuration).isEmpty()
        rCache.updateReceipts(apiId, [], newStatus, newDuration).isEmpty()
    }

    void "test updating receipts"() {
        given:
        ReceiptStatus newStatus = ReceiptStatus.SUCCESS
        Integer newDuration = TestUtils.randIntegerUpTo(100, true)
        Integer invalidDuration = -88
        String apiId = TestUtils.randString()
        Long rptId = TestUtils.randIntegerUpTo(88)

        RecordItemReceipt rpt1 = TestUtils.buildReceipt()
        MockedMethod getAllIds = TestUtils.mock(AsyncUtils, "getAllIds") { [rpt1] }

        when: "only status"
        List resList = rCache.updateReceipts(apiId, [rptId], newStatus)

        then:
        getAllIds.callCount == 1
        rpt1.status == newStatus
        rpt1.numBillable == null
        resList == [rpt1.toInfo()]

        when: "both status and duration"
        resList = rCache.updateReceipts(apiId, [rptId], newStatus, newDuration)

        then:
        getAllIds.callCount == 2
        rpt1.status == newStatus
        rpt1.numBillable == newDuration
        resList == [rpt1.toInfo()]

        when: "invalid inputs"
        resList = rCache.updateReceipts(apiId, [rptId], null, invalidDuration)

        then: "errors are logged and failing receipts not returned"
        getAllIds.callCount == 3
        resList.isEmpty()

        cleanup:
        getAllIds.restore()
    }
}
