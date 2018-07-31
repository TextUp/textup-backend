package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.type.ReceiptStatus
import org.textup.util.CustomSpec
import spock.lang.*

class RecordItemStatusSpec extends CustomSpec {

    void "test adding and retrieving receipts grouped by status"() {
        given: "empty obj"
        RecordItemStatus rStat1 = new RecordItemStatus()

        when: "add several receipts"
        int numFailed = 2
        int numPending = 4
        int numBusy = 8
        int numSuccess = 3
        numFailed.times {
            rStat1.add(new RecordItemReceipt(
                contactNumberAsString: randPhoneNumber(),
                status: ReceiptStatus.FAILED))
        }
        numPending.times {
            rStat1.add(new RecordItemReceipt(
                contactNumberAsString: randPhoneNumber(),
                status: ReceiptStatus.PENDING))
        }
        numBusy.times {
            rStat1.add(new RecordItemReceipt(
                contactNumberAsString: randPhoneNumber(),
                status: ReceiptStatus.BUSY))
        }
        numSuccess.times {
            rStat1.add(new RecordItemReceipt(
                contactNumberAsString: randPhoneNumber(),
                status: ReceiptStatus.SUCCESS))
        }

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
