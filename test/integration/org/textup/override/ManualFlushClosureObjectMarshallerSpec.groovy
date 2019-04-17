package org.textup.override

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class ManualFlushClosureObjectMarshallerSpec extends Specification {

    void "test marshalling without flushing"() {
        given:
        Organization org1 = TestUtils.buildOrg()

        org1.name = TestUtils.randString()
        assert org1.isDirty()
        org1.save()
        assert org1.isDirty()

        int marshalCount = 0
        ManualFlushClosureObjectMarshaller marshaller = new ManualFlushClosureObjectMarshaller(Map, { Map test ->
            marshalCount++
            Organization.exists(-88L) // would normally flush
            test
        })

        when: "execute closure that contains code that would normally trigger flush"
        marshaller.marshalObject([:], null)

        then: "marshal closure executed but org instance still isn't flushed"
        marshalCount == 1
        org1.isDirty()
    }
}
