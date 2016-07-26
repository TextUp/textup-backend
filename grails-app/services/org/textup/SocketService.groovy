package org.textup

import com.pusher.rest.data.Result as PResult
import com.pusher.rest.data.Result.Status as PStatus
import com.pusher.rest.Pusher
import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.http.HttpStatus

@GrailsTypeChecked
@Transactional(readOnly=true)
class SocketService {

    GrailsApplication grailsApplication
    Pusher pusherService
    ResultFactory resultFactory

    ResultList<Staff> sendItems(List<RecordItem> items,
        String eventName=Constants.SOCKET_EVENT_RECORDS) {
        if (!items) {
            return new ResultList<Staff>()
        }
        send(items*.record, items, eventName)
    }
    ResultList<Staff> sendContacts(List<Contact> contacts,
        String eventName=Constants.SOCKET_EVENT_CONTACTS) {
        if (!contacts) {
            return new ResultList<Staff>()
        }
        send(contacts*.record, contacts, eventName)
    }
    ResultList<Staff> sendFutureMessages(List<FutureMessage> fMsgs,
        String eventName=Constants.SOCKET_EVENT_FUTURE_MESSAGES) {
        if (!fMsgs) {
            return new ResultList<Staff>()
        }
        // refresh trigger so we get the most up-to-date job detail info
        fMsgs.each({ FutureMessage fMsg -> fMsg.refreshTrigger() })
        send(fMsgs*.record, fMsgs, eventName)
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected ResultList<Staff> send(List<Record> recs, List toBeSent, String eventName) {
        ResultList<Staff> resList = new ResultList<>()
        if (!toBeSent) {
            return resList
        }
        HashSet<Staff> staffList = getStaffsForRecords(recs)
        List serialized
        JSON.use(grailsApplication.flatConfig["textup.rest.defaultLabel"]) {
            serialized = new JsonSlurper().parseText((toBeSent as JSON).toString())
        }
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
                Map channelInfo = Helpers.toJson(pRes.message) as Map
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
            resultFactory.failWithMessageAndStatus(stat, pRes.message)
        }
        catch (e) {
            resultFactory.failWithThrowable(e)
        }
    }
}
