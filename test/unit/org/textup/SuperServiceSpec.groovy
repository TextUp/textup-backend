package org.textup

import grails.gorm.DetachedCriteria
import grails.test.mixin.TestFor
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import spock.lang.Specification

@TestFor(SuperService)
class SuperServiceSpec extends Specification {

    void "test finding admins for org id"() {
        given:
        Long orgId = TestUtils.randIntegerUpTo(88)

        Staff s1 = GroovyMock()
        MockedMethod buildForOrgIdAndOptions = MockedMethod.create(Staffs, "buildForOrgIdAndOptions") {
            GroovyStub(DetachedCriteria) { list() >> [s1] }
        }

        when:
        Collection admins = service.getAdminsForOrgId(orgId)

        then:
        buildForOrgIdAndOptions.latestArgs == [orgId, null, [StaffStatus.ADMIN]]
        admins == [s1]

        cleanup:
        buildForOrgIdAndOptions?.restore()
    }
}
