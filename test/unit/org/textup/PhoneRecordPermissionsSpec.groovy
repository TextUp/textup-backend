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
class PhoneRecordPermissionsSpec extends Specification {

    void "test creation"() {
        given:
        DateTime dt = DateTime.now()
        SharePermission perm1 = SharePermission.values()[0]

        when:
        PhoneRecordPermissions permissions = new PhoneRecordPermissions(dt, perm1)

        then:
        permissions.level == perm1
    }

    void "test properties"() {
        given:
        DateTime expiredDt = DateTime.now().minusDays(1)
        DateTime futureDt = DateTime.now().plusDays(1)

        when:
        PhoneRecordPermissions permissions = new PhoneRecordPermissions(null, null)

        then:
        permissions.level == null
        permissions.isOwner() == true
        permissions.isNotExpired() == true
        permissions.canModify() == true
        permissions.canView() == true

        when:
        permissions = new PhoneRecordPermissions(expiredDt, SharePermission.DELEGATE)

        then:
        permissions.level == SharePermission.DELEGATE
        permissions.isOwner() == false
        permissions.isNotExpired() == false
        permissions.canModify() == false
        permissions.canView() == false

        when:
        permissions = new PhoneRecordPermissions(expiredDt, SharePermission.VIEW)

        then:
        permissions.level == SharePermission.VIEW
        permissions.isOwner() == false
        permissions.isNotExpired() == false
        permissions.canModify() == false
        permissions.canView() == false

        when:
        permissions = new PhoneRecordPermissions(futureDt, SharePermission.DELEGATE)

        then:
        permissions.level == SharePermission.DELEGATE
        permissions.isOwner() == false
        permissions.isNotExpired() == true
        permissions.canModify() == true
        permissions.canView() == true

        when:
        permissions = new PhoneRecordPermissions(futureDt, SharePermission.VIEW)

        then:
        permissions.level == SharePermission.VIEW
        permissions.isOwner() == false
        permissions.isNotExpired() == true
        permissions.canModify() == false
        permissions.canView() == true
    }

    void "test owner cannot be expired even if dated expired is set"() {
        given:
        DateTime dt = DateTime.now().minusDays(1)

        when:
        PhoneRecordPermissions permissions = new PhoneRecordPermissions(dt, null)

        then: "if shared, this would be expired since dateExpired is in the past, but this is owner"
        permissions.isOwner() == true
        permissions.isNotExpired() == true
        permissions.canModify() == true
        permissions.canView() == true
    }
}
