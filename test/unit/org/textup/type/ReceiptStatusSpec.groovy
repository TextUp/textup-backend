package org.textup.type

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
}
