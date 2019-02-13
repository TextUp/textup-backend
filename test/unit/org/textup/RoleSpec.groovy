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

@TestFor(Role)
class RoleSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test static creation"() {
        given:
        String authority = TestUtils.randString()

        when:
        Result res = Role.tryCreate(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Role.tryCreate(authority)

        then:
        res.status == ResultStatus.CREATED
        res.payload.authority == authority
    }
}
