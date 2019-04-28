package org.textup.type

import grails.test.mixin.*
import grails.test.mixin.support.*
import org.textup.*
import org.textup.test.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class SharePermissionSpec extends Specification {

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test building summary"() {
        given:
        String name1 = TestUtils.randString()

        expect:
        SharePermission.DELEGATE.buildSummary(null) == ""
        SharePermission.DELEGATE.buildSummary([]) == ""

        and:
        SharePermission.DELEGATE.buildSummary([name1]) == "sharePermission.delegate"
        SharePermission.VIEW.buildSummary([name1]) == "sharePermission.view"
        SharePermission.NONE.buildSummary([name1]) == "sharePermission.stop"
    }
}
