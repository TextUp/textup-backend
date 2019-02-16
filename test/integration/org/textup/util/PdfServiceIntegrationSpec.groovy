package org.textup.util

import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

class PdfServiceIntegrationSpec extends Specification {

    PdfService pdfService

    void "test building record items"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(gpr1.record)
        RecordItem rItem3 = TestUtils.buildRecordItem(gpr1.record)

        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)

        when: "no input"
        Result res = pdfService.buildRecordItems(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when: "has input"
        res = pdfService.buildRecordItems(iReq)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof byte[]
        res.payload.size() > 4
        // at a minimum, ensure that the byte array has the appropriate file signature
        // see: https://en.wikipedia.org/wiki/List_of_file_signatures
        res.payload[0] == 0x25 // %
        res.payload[1] == 0x50 // P
        res.payload[2] == 0x44 // D
        res.payload[3] == 0x46 // F
        res.payload[4] == 0x2D // -
    }
}
