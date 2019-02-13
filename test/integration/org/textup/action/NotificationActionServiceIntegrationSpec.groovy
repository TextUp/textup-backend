package org.textup.action

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*
import spock.lang.*

// For some reason, adding to and removing from whitelist/blacklist is not correctly mocked
// in unit tests, must run this as an integration test
// see: https://github.com/grails/grails-datastore-test-support/issues/1

class NotificationActionServiceIntegrationSpec extends Specification {

    NotificationActionService notificationActionService

    void "test has actions"() {
        expect:
        notificationActionService.hasActions(doNotificationActions: null) == false
        notificationActionService.hasActions(doNotificationActions: "hi")
    }

    void "test handling actions"() {
        given:
        Long recId = TestUtils.randIntegerUpTo(88)
        String str1 = TestUtils.randString()
        Phone p1 = TestUtils.buildTeamPhone()
        Staff s1 = TestUtils.buildStaff()

        int opBaseline = OwnerPolicy.count()

        NotificationAction a1 = GroovyMock()
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1])
        }

        when:
        Result res = notificationActionService.tryHandleActions(Tuple.create(null, null), [doNotificationActions: str1])

        then:
        res.status == ResultStatus.BAD_REQUEST
        tryProcess.notCalled
        OwnerPolicy.count() == opBaseline

        when:
        res = notificationActionService.tryHandleActions(Tuple.create(p1, recId), [doNotificationActions: str1])

        then:
        tryProcess.latestArgs == [NotificationAction, str1]
        1 * a1.toString() >> NotificationAction.ENABLE
        1 * a1.id >> s1.id
        OwnerPolicy.findByOwnerAndStaff(p1.owner, s1).whitelist.contains(recId)
        !OwnerPolicy.findByOwnerAndStaff(p1.owner, s1).blacklist
        OwnerPolicy.count() == opBaseline + 1

        when:
        res = notificationActionService.tryHandleActions(Tuple.create(p1, recId), [doNotificationActions: str1])

        then:
        tryProcess.latestArgs == [NotificationAction, str1]
        1 * a1.toString() >> NotificationAction.DISABLE
        1 * a1.id >> s1.id
        !OwnerPolicy.findByOwnerAndStaff(p1.owner, s1).whitelist
        OwnerPolicy.findByOwnerAndStaff(p1.owner, s1).blacklist.contains(recId)
        OwnerPolicy.count() == opBaseline + 1

        cleanup:
        tryProcess?.restore()
    }
}
