package org.textup.type

import org.textup.test.*
import spock.lang.*

class ReceiptStatusSpec extends Specification {

    void "test converting strings into enum"() {
        expect: "case insensitive, input will be lowercased"
        ReceiptStatus.translate(null) == null
        ReceiptStatus.translate("invalid receipt status") == null
        ReceiptStatus.translate("ComPLETED") == ReceiptStatus.SUCCESS
        ReceiptStatus.translate("NO-ANSWER") == ReceiptStatus.BUSY
        ReceiptStatus.translate("in-progreSS") == ReceiptStatus.PENDING
        ReceiptStatus.translate("SENT") == ReceiptStatus.SUCCESS
    }

    void "test determining if is earlier in sequence"() {
        expect:
        ReceiptStatus.PENDING.isEarlierInSequenceThan(ReceiptStatus.PENDING) == false
        ReceiptStatus.PENDING.isEarlierInSequenceThan(ReceiptStatus.SUCCESS) == true
        ReceiptStatus.PENDING.isEarlierInSequenceThan(ReceiptStatus.BUSY) == true
        ReceiptStatus.PENDING.isEarlierInSequenceThan(ReceiptStatus.FAILED) == true

        ReceiptStatus.SUCCESS.isEarlierInSequenceThan(ReceiptStatus.PENDING) == false
        ReceiptStatus.SUCCESS.isEarlierInSequenceThan(ReceiptStatus.SUCCESS) == false
        ReceiptStatus.SUCCESS.isEarlierInSequenceThan(ReceiptStatus.BUSY) == true
        ReceiptStatus.SUCCESS.isEarlierInSequenceThan(ReceiptStatus.FAILED) == true

        ReceiptStatus.BUSY.isEarlierInSequenceThan(ReceiptStatus.PENDING) == false
        ReceiptStatus.BUSY.isEarlierInSequenceThan(ReceiptStatus.SUCCESS) == false
        ReceiptStatus.BUSY.isEarlierInSequenceThan(ReceiptStatus.BUSY) == false
        ReceiptStatus.BUSY.isEarlierInSequenceThan(ReceiptStatus.FAILED) == true

        ReceiptStatus.FAILED.isEarlierInSequenceThan(ReceiptStatus.PENDING) == false
        ReceiptStatus.FAILED.isEarlierInSequenceThan(ReceiptStatus.SUCCESS) == false
        ReceiptStatus.FAILED.isEarlierInSequenceThan(ReceiptStatus.BUSY) == false
        ReceiptStatus.FAILED.isEarlierInSequenceThan(ReceiptStatus.FAILED) == false
    }
}
