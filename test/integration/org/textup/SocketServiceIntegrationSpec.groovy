package org.textup

import grails.test.runtime.DirtiesRuntime
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*

class SocketServiceIntegrationSpec extends CustomSpec {

	SocketService socketService

    def setup() {
    	setupIntegrationData()
    	socketService.metaClass.getStaffsForRecords = { List<Record> recs ->
    		HashSet<Phone> res = new HashSet<>()
			res << s1
			res
    	}
    	socketService.metaClass.sendToDataToStaff = { Staff staff, String eventName,
    		Object data ->
    		new Result(status:ResultStatus.OK, payload:[
    			staff:staff,
    			eventName:eventName,
    			data:data
			])
    	}
    }
    def cleanup() {
    	cleanupIntegrationData()
    }

    @DirtiesRuntime
    void "test sending generic object with record"() {
        given:
        int numBatches = 3
        Collection<?> toSend = []
        (3 * Constants.SOCKET_PAYLOAD_BATCH_SIZE).times { toSend << c1 }

        when:
        String eventName = "Ting Ting"
        ResultGroup<Staff> resGroup = socketService.send([c1.record], toSend, eventName)

        then:
        resGroup.payload.size() == numBatches
        resGroup.payload.every { it.staff == s1 }
        resGroup.payload.every { it.eventName == eventName }
        resGroup.payload.every { it.data instanceof List }
        resGroup.payload.every { it.data.size() <= Constants.SOCKET_PAYLOAD_BATCH_SIZE }
        resGroup.payload.every { it.data[0].id == c1.id }
    }

    void "test sending future messages"() {
        given:
        FutureMessage fMsg = Mock(FutureMessage)

        when:
        ResultGroup<Staff> resGroup = socketService.sendFutureMessages([fMsg])

        then:
        1 * fMsg.refreshTrigger()
        resGroup.successes.size() == 1
    }

    @DirtiesRuntime
    void "test sending a phone"() {
        given:
        MockedMethod sendToDataToStaff = TestUtils.mock(socketService, "sendToDataToStaff") {
            new Result()
        }

        when:
        ResultGroup<Staff> resGroup = socketService.sendPhone(null)

        then:
        resGroup.isEmpty == true
        sendToDataToStaff.callCount == 0

        when:
        resGroup = socketService.sendPhone(p1)

        then:
        resGroup.successes.size() == p1.owner.buildAllStaff().size()
        sendToDataToStaff.callCount == p1.owner.buildAllStaff().size()
    }
}
