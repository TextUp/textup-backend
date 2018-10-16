package org.textup.rest

import grails.test.mixin.TestFor
import org.textup.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*
import static javax.servlet.http.HttpServletResponse.*

@TestFor(NotifyController)
class NotifyControllerSpec extends Specification {

    void "test claim notification"() {
        given:
        controller.notificationService = Mock(NotificationService)
        Notification notif = Mock()
        String tokenId = TestHelpers.randString()

    	when:
    	request.method = "GET"
        params.id = tokenId
        controller.show()

    	then:
        1 * controller.notificationService.show(tokenId) >> new Result(payload: notif)
    	response.status == SC_OK
    	response.json != null
    }
}
