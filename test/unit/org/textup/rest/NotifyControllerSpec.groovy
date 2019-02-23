package org.textup.rest

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
import spock.lang.*

// For some reason all controllers that have the `@Transactional` annotation must have
// `HibernateTestMixin` mixed for proper initialization of transaction manager
// see https://stackoverflow.com/a/25865276

@TestFor(NotifyController)
@TestMixin(HibernateTestMixin)
class NotifyControllerSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test show"() {
        given:
        String tokenId = TestUtils.randString()

        controller.notificationService = GroovyMock(NotificationService)

    	when:
        params.id = tokenId
        controller.show()

    	then:
        1 * controller.notificationService.redeem(tokenId) >> Result.void()
    	response.status == ResultStatus.NO_CONTENT.intStatus
    }
}
