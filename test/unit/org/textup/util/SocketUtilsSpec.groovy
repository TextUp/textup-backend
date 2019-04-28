package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class SocketUtilsSpec extends Specification {

    void "test channel name to user name conversion"() {
        given:
        String username = TestUtils.randString()
        Staff s1 = Stub() { getUsername() >> username }

        expect:
        SocketUtils.channelToUserName(SocketUtils.channelName(s1)) == username
    }
}
