package org.textup.test

import org.textup.*
import spock.lang.*

class OutputStreamCaptorSpec extends Specification {

    void "test capturing and restoring output streams"() {
        given:
        String msg1 = TestUtils.randString()
        String msg2 = TestUtils.randString()
        String msg3 = TestUtils.randString()
        String msg4 = TestUtils.randString()
        OutputStreamCaptor captor = new OutputStreamCaptor()

        when: "capture stream"
        Tuple<ByteArrayOutputStream, ByteArrayOutputStream> streams = captor.capture()
        System.out.println(msg1)
        System.err.println(msg2)

        then:
        streams.first instanceof ByteArrayOutputStream
        streams.second instanceof ByteArrayOutputStream
        streams.first.toString().contains(msg1)
        streams.first.toString().contains(msg2) == false
        streams.second.toString().contains(msg1) == false
        streams.second.toString().contains(msg2)

        when: "restore stream"
        captor.restore()
        System.out.println(msg3)
        System.err.println(msg4)

        then:
        streams.first.toString().contains(msg3) == false
        streams.first.toString().contains(msg4) == false
        streams.second.toString().contains(msg3) == false
        streams.second.toString().contains(msg4) == false
    }
}
