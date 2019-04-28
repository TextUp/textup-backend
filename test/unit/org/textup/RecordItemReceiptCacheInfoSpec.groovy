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
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class RecordItemReceiptCacheInfoSpec extends Specification {

    void "test creation"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)
        Long itemId = TestUtils.randIntegerUpTo(88)
        RecordItem rItem1 = Mock()
        ReceiptStatus status = ReceiptStatus.values()[0]
        Integer numBillable = TestUtils.randIntegerUpTo(88)

        when:
        RecordItemReceiptCacheInfo cacheInfo = RecordItemReceiptCacheInfo.create(id, rItem1, status, numBillable)

        then:
        1 * rItem1.id >> itemId
        cacheInfo.id == id
        cacheInfo.itemId == itemId
        cacheInfo.status == status
        cacheInfo.numBillable == numBillable
    }
}
