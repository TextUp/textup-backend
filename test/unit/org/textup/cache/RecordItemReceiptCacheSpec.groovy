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
import spock.lang.*

@Domain([CustomAccountDetails, Record, RecordItem, RecordText, RecordCall, RecordItemReceipt,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordItemReceiptCacheSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    @DirtiesRuntime
    void "test finding all receipts by api id"() {
        given:
        RecordItemReceiptCache rCache = new RecordItemReceiptCache()
        RecordItemReceipt mockRpt = GroovyMock()
        RecordItemReceipt.metaClass."static".findAllByApiId = { String apiId -> [mockRpt] }
        String apiId = TestUtils.randString()

        when:
        List<RecordItemReceipt> rptList = rCache.findReceiptsByApiId(apiId)

        then:
        rptList == [mockRpt]
    }

    void "test updating receipts no receipts"() {
        given:
        ReceiptStatus newStatus = ReceiptStatus.SUCCESS
        Integer newDuration = TestUtils.randIntegerUpTo(100, true)

        RecordItemReceiptCache rCache = new RecordItemReceiptCache()

        expect:
        rCache.updateReceipts(null, newStatus, newDuration).isEmpty()
        rCache.updateReceipts([], newStatus, newDuration).isEmpty()
    }

    void "test updating receipts"() {
        given:
        ReceiptStatus newStatus = ReceiptStatus.SUCCESS
        Integer newDuration = TestUtils.randIntegerUpTo(100, true)
        Integer invalidDuration = -88
        RecordItemReceiptCache rCache = new RecordItemReceiptCache()
        RecordItemReceipt mockRpt = GroovyMock() { asBoolean() >> true }

        Record rec1 = new Record()
        rec1.save(flush: true, failOnError: true)
        RecordItem rItem = new RecordItem(record: rec1)
        rItem.save(flush: true, failOnError: true)
        RecordItemReceipt rpt1 = new RecordItemReceipt(item: rItem,
            contactNumberAsString: TestUtils.randPhoneNumber(),
            apiId: TestUtils.randString())
        assert rpt1.validate()

        when: "only status"
        List<RecordItemReceipt> rptList = rCache.updateReceipts([mockRpt], newStatus)

        then:
        1 * mockRpt.setStatus(newStatus)
        0 * mockRpt.setNumBillable(*_)
        1 * mockRpt.merge() >> mockRpt
        rptList == [mockRpt]

        when: "both status and duration"
        rptList = rCache.updateReceipts([mockRpt], newStatus, newDuration)

        then:
        1 * mockRpt.setStatus(newStatus)
        1 * mockRpt.setNumBillable(newDuration)
        1 * mockRpt.merge() >> mockRpt
        rptList == [mockRpt]

        when: "invalid inputs"
        rptList = rCache.updateReceipts([rpt1], null, invalidDuration)

        then: "errors are logged and failing receipts not returned"
        rptList.isEmpty()
    }
}
