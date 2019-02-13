package org.textup.type

import org.textup.*
import org.textup.test.*
import spock.lang.*

class RecordItemTypeSpec extends Specification {

    void "test converting to class"() {
        expect:
        RecordItemType.CALL.toClass() == RecordCall
        RecordItemType.TEXT.toClass() == RecordText
        RecordItemType.NOTE.toClass() == RecordNote
    }
}
