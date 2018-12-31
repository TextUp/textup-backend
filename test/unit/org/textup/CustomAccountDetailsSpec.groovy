package org.textup

import grails.test.mixin.TestFor
import org.textup.test.*
import spock.lang.Specification

@TestFor(CustomAccountDetails)
class CustomAccountDetailsSpec extends Specification {

    void "validation"() {
        when: "empty"
        CustomAccountDetails cad1 = new CustomAccountDetails()

        then:
        cad1.validate() == false

        when: "filled in with empty string"
        cad1.accountId = ""
        cad1.authToken = ""

        then:
        cad1.validate() == false

        when: "filled with non-empty strings"
        cad1.accountId = TestUtils.randString()
        cad1.authToken = TestUtils.randString()

        then:
        cad1.validate()
    }
}
