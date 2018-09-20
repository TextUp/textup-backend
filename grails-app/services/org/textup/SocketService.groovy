package org.textup

import com.pusher.rest.data.Result as PusherResult
import com.pusher.rest.data.Result.Status as PusherStatus
import com.pusher.rest.Pusher
import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.grails.commons.GrailsApplication

@GrailsTypeChecked
@Transactional(readOnly=true)
class SocketService {

    GrailsApplication grailsApplication
    Pusher pusherService
    ResultFactory resultFactory

    ResultGroup<Staff> sendItems(Collection<? extends RecordItem> items,
        String eventName = Constants.SOCKET_EVENT_RECORDS) {
        if (!items) {
            return new ResultGroup<Staff>()
        }
        send(items*.record, items, eventName)
    }
    ResultGroup<Staff> sendContacts(Collection<Contact> contacts,
        String eventName = Constants.SOCKET_EVENT_CONTACTS) {
        if (!contacts) {
            return new ResultGroup<Staff>()
        }
        send(contacts*.record, contacts, eventName)
    }
    ResultGroup<Staff> sendFutureMessages(Collection<FutureMessage> fMsgs,
        String eventName = Constants.SOCKET_EVENT_FUTURE_MESSAGES) {
        if (!fMsgs) {
            return new ResultGroup<Staff>()
        }
        // refresh trigger so we get the most up-to-date job detail info
        fMsgs.each({ FutureMessage fMsg -> fMsg.refreshTrigger() })
        send(fMsgs*.record, fMsgs, eventName)
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected ResultGroup<Staff> send(Collection<Record> recs, Collection toBeSent,
        String eventName) {

        ResultGroup<Staff> resGroup = new ResultGroup<>()
        if (!toBeSent) {
            return resGroup
        }
        HashSet<Staff> staffList = getStaffsForRecords(recs)
        Collection serialized
        JSON.use(grailsApplication.flatConfig["textup.rest.defaultLabel"]) {
            serialized = new JsonSlurper().parseText((toBeSent as JSON).toString())
        }
        staffList.each { Staff s1 ->
            resGroup << sendToDataToStaff(s1, eventName, serialized)
        }
        resGroup
    }

    // Helper methods
    // --------------

    protected HashSet<Staff> getStaffsForRecords(Collection<Record> recs) {
        HashSet<Phone> phones = Phone.getPhonesForRecords(recs)
        HashSet<Staff> staffs = new HashSet<Staff>()
        phones.each { staffs.addAll(it.owner.all) }
        staffs
    }
    protected Result<Staff> sendToDataToStaff(Staff s1, String eventName, Object data) {
        String channelName = "private-${s1.username}"
        PusherResult pRes = pusherService.get("/channels/$channelName")
        if (pRes.status == PusherStatus.SUCCESS) {
            try {
                Map channelInfo = Helpers.toJson(pRes.message) as Map
                if (channelInfo.occupied) {
                    pRes = pusherService.trigger(channelName, eventName, data)
                    if (pRes.status == PusherStatus.SUCCESS) {
                        resultFactory.success(s1)
                    }
                    else { resultFactory.failForPusher(pRes) }
                }
                else { resultFactory.success(s1) }
            }
            catch (Throwable e) {
                log.error("SocketService.sendToDataToStaff: error: ${e.message}")
                e.printStackTrace()
                resultFactory.failWithThrowable(e)
            }
        }
        else {
            log.error("SocketService.sendToDataToStaff: error: ${pRes}, \
                pRes.status: ${pRes.status}, \
                pRes.message: ${pRes.message}")
            resultFactory.failForPusher(pRes)
        }
    }
}
