package org.textup

import com.pusher.rest.data.Result
import com.pusher.rest.data.Result.Status
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper

@Transactional(readOnly=true)
class SocketService {

    def grailsApplication
    def pusherService
    def authService

    void sendItems(List<RecordItem> items, String eventName=Constants.SOCKET_EVENT_RECORDS) {
        if (!items) { return }
        HashSet<Staff> staffList = authService.getPhonesForRecords(items*.record)
        Map serialized
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            serialized = new JsonSlurper().parseText((items as JSON).toString())
        }
        staffList.each { Staff s1 -> sendToDataToStaff(s1, eventName, serialized) }
    }
    void sendContacts(List<Contact> contacts, String eventName=Constants.SOCKET_EVENT_CONTACTS) {
        HashSet<Staff> staffList = authService.getPhonesForRecords(contacts*.record)
        Map serialized
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            serialized = new JsonSlurper().parseText((contacts as JSON).toString())
        }
        staffList.each { Staff s1 -> sendToDataToStaff(s1, eventName, serialized) }
    }

    // Helper methods
    // --------------

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
            log.error("SocketService.sendToDataToStaff: error: ${pusherRes}, \
                pusherRes.status: ${pusherRes.status}, \
                pusherRes.message: ${pusherRes.message}")
        }
    }
}
