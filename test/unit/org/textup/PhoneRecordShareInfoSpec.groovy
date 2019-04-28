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

@TestMixin(GrailsUnitTestMixin)
class PhoneRecordShareInfoSpec extends Specification {

    void "test creation"() {
        given:
        DateTime dt = DateTime.now()
        Phone p1 = Stub() { getId() >> TestUtils.randIntegerUpTo(88) }
        SharePermission perm1 = SharePermission.values()[0]

        when:
        PhoneRecordShareInfo sInfo = PhoneRecordShareInfo.create(dt, p1, perm1)

        then:
        sInfo.whenCreated == dt
        sInfo.phoneId == p1.id
        sInfo.permission == perm1.toString()
    }
}
