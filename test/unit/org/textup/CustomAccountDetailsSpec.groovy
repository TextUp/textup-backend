package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

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
