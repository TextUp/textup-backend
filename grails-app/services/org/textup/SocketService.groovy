package org.textup

import com.pusher.rest.data.Result
import com.pusher.rest.data.Result.Status
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper

@Transactional
class SocketService {

    def grailsApplication
    def pusherService

    @Transactional(readOnly=true)
    void sendRecord(RecordItem item, String eventName=Constants.SOCKET_EVENT_RECORD) {
        Contact c1 = Contact.forRecord(item?.record).list()[0]
        if (c1) {
            List<Staff> staffList = getStaffIdsForContactOrShared(c1)
            Map serialized
            JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
                serialized = new JsonSlurper().parseText((item as JSON).toString())
            }
            staffList.each { Staff s1 -> sendToDataToStaff(s1, eventName, serialized) }
        }
    }
    void sendContact(Contact c1, String eventName=Constants.SOCKET_EVENT_NEW_CONTACT) {
        List<Staff> staffList = getStaffIdsForContactOrShared(c1)
        Map serialized
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            serialized = new JsonSlurper().parseText((c1 as JSON).toString())
        }
        staffList.each { Staff s1 -> sendToDataToStaff(s1, eventName, serialized) }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    protected List<Long> getStaffIdsForContactOrShared(Contact c1) {
        List<Long> sWithStaffIds = SharedContact.nonexpiredForContact(c1).list()?.collect {
            it.sharedWith.ownerId
        }
        List<Staff> staffList = Staff.getAll(sWithStaffIds)
        Staff foundStaff = Staff.forContactId(c1.id).get()
        if (foundStaff) {
            staffList << foundStaff
        }
        else {
            log.error("SocketService.getStaffIdsForContactOrShared: could not find staff for contact: $c1")
        }
        return staffList
    }

    protected void sendToDataToStaff(Staff s1, String eventName, Map data) {
        String channelName = "private-${s1.username}"
        Result pusherRes = pusherService.get("/channels/$channelName")
        if (pusherRes.status == Status.SUCCESS) {
            try {
                Map channelInfo = new JsonSlurper().parseText(pusherRes.message)
                if (channelInfo.occupied) {
                    pusherService.trigger(channelName, eventName, data)
                }
            }
            catch (e) {
                log.error("SocketService.sendToDataToStaff: error: ${e.message}")
            }
        }
        else {
            log.error("SocketService.sendToDataToStaff: error: ${pusherRes}, pusherRes.status: ${pusherRes.status}, pusherRes.message: ${pusherRes.message}")
        }
    }
}
