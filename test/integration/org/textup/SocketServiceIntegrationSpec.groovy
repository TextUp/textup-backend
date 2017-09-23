package org.textup

import grails.test.runtime.DirtiesRuntime
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.util.CustomSpec

class SocketServiceIntegrationSpec extends CustomSpec {

	def socketService

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
        when:
        String eventName = "Ting Ting"
        ResultGroup<Staff> resGroup = socketService.send([c1.record], [c1], eventName)

        then:
        resGroup.payload.size() == 1
        resGroup.payload[0].staff == s1
        resGroup.payload[0].eventName == eventName
        resGroup.payload[0].data instanceof List
        resGroup.payload[0].data.size() == 1
        resGroup.payload[0].data[0].id == c1.id
    }
}
