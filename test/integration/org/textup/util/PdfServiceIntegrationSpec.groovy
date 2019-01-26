package org.textup.util

import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

class PdfServiceIntegrationSpec extends CustomSpec {

    PdfService pdfService

    def setup() {
        setupIntegrationData()
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    void "test building record items"() {
        given:
        RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
        c1.phone = p1
        RecordItem rItem1 = c1.record.storeOutgoingText(TestUtils.randString()).payload
        tag1.phone = p1
        RecordItem rItem2 = tag1.record.storeOutgoingCall().payload
        RecordItem rItem3 = tag1.record.storeOutgoingCall().payload

        Record.withSession { it.flush() }

        when: "no input"
        Result<byte[]> res = pdfService.buildRecordItems(null)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages.size() == 1

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
