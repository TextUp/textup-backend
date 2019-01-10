package org.textup.util

import com.pusher.rest.data.Result as PusherResult
import com.pusher.rest.data.Result.Status as PusherStatus
import com.pusher.rest.Pusher
import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper
import groovy.transform.TypeCheckingMode
import org.textup.util.*

@Transactional(readOnly=true)
class SocketService {

    Pusher pusherService
    ResultFactory resultFactory

    @GrailsTypeChecked
    ResultGroup<Staff> sendItems(Collection<? extends RecordItem> items) {
        if (!items) {
            return new ResultGroup<Staff>()
        }
        send(items*.record, items, Constants.SOCKET_EVENT_RECORDS)
    }

    @GrailsTypeChecked
    ResultGroup<Staff> sendContacts(Collection<Contact> contacts) {
        if (!contacts) {
            return new ResultGroup<Staff>()
        }
        send(contacts*.record, contacts, Constants.SOCKET_EVENT_CONTACTS)
    }

    @GrailsTypeChecked
    ResultGroup<Staff> sendFutureMessages(Collection<FutureMessage> fMsgs) {
        if (!fMsgs) {
            return new ResultGroup<Staff>()
        }
        // refresh trigger so we get the most up-to-date job detail info
        fMsgs.each({ FutureMessage fMsg -> fMsg.refreshTrigger() })
        Collection<Record> recs = fMsgs.collect { FutureMessage fMsg -> fMsg.getRecord() }
        send(recs, fMsgs, Constants.SOCKET_EVENT_FUTURE_MESSAGES)
    }

    ResultGroup<Staff> sendPhone(Phone p1) {
        ResultGroup<Staff> resGroup = new ResultGroup<>()
        if (!p1) {
            return resGroup
        }
        Collection serialized = DataFormatUtils.jsonToObject([p1])
        p1.owner.buildAllStaff().each { Staff s1 ->
            resGroup << sendToDataToStaff(s1, Constants.SOCKET_EVENT_PHONES, serialized)
        }
        resGroup
    }

    // Helper methods
    // --------------

    protected ResultGroup<Staff> send(Collection<Record> recs, Collection<?> toSend, String event) {
        ResultGroup<Staff> resGroup = new ResultGroup<>()
        if (!toSend) { return resGroup }
        toSend
            .collate(Constants.SOCKET_PAYLOAD_BATCH_SIZE) // prevent exceeding payload max size
            .each { Collection<?> batch ->
                Collection serialized = DataFormatUtils.jsonToObject(batch)
                getStaffsForRecords(recs).each { Staff s1 ->
                    resGroup << sendToDataToStaff(s1, event, serialized)
                }
            }
        resGroup
    }

    @GrailsTypeChecked
    protected Collection<Staff> getStaffsForRecords(Collection<Record> recs) {
        HashSet<Phone> phones = Phones.findEveryForRecords(recs)
        HashSet<Staff> staffList = new HashSet<>()
        phones*.owner.each { PhoneOwnership po1 -> staffList.addAll(po1.buildAllStaff()) }
        staffList
    }

    @GrailsTypeChecked
    protected Result<Staff> sendToDataToStaff(Staff s1, String eventName, Object data) {
        String channelName = s1.channelName
        PusherResult pRes = pusherService.get("/channels/$channelName")
        if (pRes.status != PusherStatus.SUCCESS) {
            log.error("SocketService.sendToDataToStaff: error: ${pRes}, \
                pRes.status: ${pRes.status}, \
                pRes.message: ${pRes.message}")
            return resultFactory.failForPusher(pRes)
        }
        try {
            Map channelInfo = DataFormatUtils.jsonToObject(pRes.message) as Map
            if (!channelInfo.occupied) {
                return resultFactory.success(s1)
            }
            pRes = pusherService.trigger(channelName, eventName, data)
            if (pRes.status == PusherStatus.SUCCESS) {
                resultFactory.success(s1)
            }
            else { resultFactory.failForPusher(pRes) }
        }
        catch (Throwable e) {
            log.error("SocketService.sendToDataToStaff: error: ${e.message}")
            e.printStackTrace()
            resultFactory.failWithThrowable(e)
        }
    }
}
