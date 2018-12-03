package org.textup

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.test.*
import org.textup.util.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class UnreadInfoSpec extends Specification {

    void "test default values"() {
        when:
        UnreadInfo uInfo = new UnreadInfo()

        then:
        uInfo.numTexts == 0
        uInfo.numCalls == 0
        uInfo.numVoicemails == 0
    }
}
