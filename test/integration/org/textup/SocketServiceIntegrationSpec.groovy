package org.textup

import grails.test.runtime.DirtiesRuntime
import org.textup.types.OrgStatus
import org.textup.types.ResultType
import org.textup.types.StaffStatus
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
    		new Result(type:ResultType.SUCCESS, success:true, payload:[
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
        ResultList<Staff> resList = socketService.send([c1.record], [c1], eventName)

        then:
        resList.results.size() == 1
        resList.results[0].payload.staff == s1
        resList.results[0].payload.eventName == eventName
        resList.results[0].payload.data instanceof List
        resList.results[0].payload.data.size() == 1
        resList.results[0].payload.data[0].id == c1.id
    }
}
