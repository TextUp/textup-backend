package org.textup.type

import org.textup.test.*
import spock.lang.*

class FutureMessageTypeSpec extends Specification {

    void "test converting to record item type"() {
        expect:
        FutureMessageType.CALL.toRecordItemType() == RecordItemType.CALL
        FutureMessageType.TEXT.toRecordItemType() == RecordItemType.TEXT
    }
}
