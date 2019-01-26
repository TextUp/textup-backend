package org.textup.rest

import org.textup.test.*
import grails.test.mixin.TestFor
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestFor(NotifyController)
class NotifyControllerSpec extends Specification {

    void "test claim notification"() {
        given:
        controller.notificationService = Mock(NotificationService)
        Notification notif = Mock()
        String tokenId = TestUtils.randString()

    	when:
    	request.method = "GET"
        params.id = tokenId
        controller.show()

    	then:
        1 * controller.notificationService.show(tokenId) >> new Result(payload: notif)
    	response.status == HttpServletResponse.SC_OK
    	response.json != null
    }
}
