package org.textup

import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import com.pusher.rest.Pusher
import com.pusher.rest.data.Result as PResult
import com.pusher.rest.data.Result.Status as PStatus
import org.springframework.http.HttpStatus

@Transactional(readOnly=true)
class SocketService {

    GrailsApplication grailsApplication
    Pusher pusherService
    ResultFactory resultFactory

    ResultList<Staff> sendItems(List<RecordItem> items,
        String eventName=Constants.SOCKET_EVENT_RECORDS) {
        ResultList<Staff> resList = new ResultList<>()
        if (!items) {
            return resList
        }
        HashSet<Staff> staffList = getStaffsForRecords(items*.record)
        List serialized = Helpers.toJson(items,
            grailsApplication.flatConfig["textup.rest.defaultLabel"])
        staffList.each { Staff s1 ->
            resList << sendToDataToStaff(s1, eventName, serialized)
        }
        resList
    }
    ResultList<Staff> sendContacts(List<Contact> contacts,
        String eventName=Constants.SOCKET_EVENT_CONTACTS) {
        ResultList<Staff> resList = new ResultList<>()
        if (!contacts) {
            return resList
        }
        HashSet<Staff> staffList = getStaffsForRecords(contacts*.record)
        List serialized = Helpers.toJson(contacts,
            grailsApplication.flatConfig["textup.rest.defaultLabel"])
        staffList.each { Staff s1 ->
            resList << sendToDataToStaff(s1, eventName, serialized)
        }
        resList
    }

    // Helper methods
    // --------------

    protected HashSet<Staff> getStaffsForRecords(List<Record> recs) {
        HashSet<Phone> phones = Phone.getPhonesForRecords(recs)
        HashSet<Staff> staffs = new HashSet<Staff>()
        phones.each { staffs.addAll(it.owner.all) }
        staffs
    }
    protected Result<Staff> sendToDataToStaff(Staff s1, String eventName, Object data) {
        String channelName = "private-${s1.username}"
        PResult pRes = pusherService.get("/channels/$channelName")
        if (pRes.status == PStatus.SUCCESS) {
            try {
                Map channelInfo = Helpers.toJson(pRes.message)
                if (channelInfo.occupied) {
                    pRes = pusherService.trigger(channelName, eventName, data)
                    if (pRes.status == PStatus.SUCCESS) {
                        resultFactory.success(s1)
                    }
                    else { convertPusherResultOnError(pRes) }
                }
                else { resultFactory.success(s1) }
            }
            catch (e) {
                log.error("SocketService.sendToDataToStaff: error: ${e.message}")
                resultFactory.failWithThrowable(e)
            }
        }
        else {
            log.error("SocketService.sendToDataToStaff: error: ${pRes}, \
                pRes.status: ${pRes.status}, \
                pRes.message: ${pRes.message}")
            convertPusherResultOnError(pRes)
        }
    }
    protected Result convertPusherResultOnError(PResult pRes) {
        try {
            HttpStatus stat = HttpStatus.valueOf(pRes.httpStatus)
            resultFactory.failWithMessagesAndStatus(stat, pRes.message)
        }
        catch (e) {
            resultFactory.failWithThrowable(e)
        }
    }
}
