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

class RecordItemReceiptInfoSpec extends Specification {

    void "test adding and retrieving receipts grouped by status"() {
        given: "empty obj"
        RecordItemReceiptInfo rStat1 = new RecordItemReceiptInfo()

        when: "add several receipts"
        int numFailed = 2
        int numPending = 4
        int numBusy = 8
        int numSuccess = 3
        numFailed.times { rStat1.add(TestUtils.buildReceipt(ReceiptStatus.FAILED)) }
        numPending.times { rStat1.add(TestUtils.buildReceipt(ReceiptStatus.PENDING)) }
        numBusy.times { rStat1.add(TestUtils.buildReceipt(ReceiptStatus.BUSY)) }
        numSuccess.times { rStat1.add(TestUtils.buildReceipt(ReceiptStatus.SUCCESS)) }

        then: "can retrieve added receipts grouped by status"
        rStat1.failed.every { it instanceof String }
        rStat1.failed.size() == numFailed
        rStat1.pending.every { it instanceof String }
        rStat1.pending.size() == numPending
        rStat1.busy.every { it instanceof String }
        rStat1.busy.size() == numBusy
        rStat1.success.every { it instanceof String }
        rStat1.success.size() == numSuccess
    }
}
