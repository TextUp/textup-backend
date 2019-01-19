package org.textup.util

import com.pusher.rest.data.Result as PusherResult
import com.pusher.rest.Pusher
import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper
import groovy.transform.TypeCheckingMode
import org.textup.util.*

@GrailsTypeChecked
@Transactional(readOnly = true)
class SocketService {

    private static final int PAYLOAD_BATCH_SIZE = 20
    private static final String EVENT_CONTACTS = "contacts"
    private static final String EVENT_FUTURE_MESSAGES = "futureMessages"
    private static final String EVENT_RECORDS_ITEMS = "recordItems"
    private static final String EVENT_PHONES = "phones"

    Pusher pusherService

    void sendItems(Collection<? extends RecordItem> items) {
        trySend(EVENT_RECORDS_ITEMS, Staffs.findEveryForRecordIds(items*.record*.id), items)
            .logFail("sendItems: `${items*.id}`")
    }

    void sendIndividualWrappers(Collection<? extends IndividualPhoneRecordWrapper> wraps) {
        ResultGroup
            .collect(wraps) { IndividualPhoneRecordWrapper w1 ->
                w1.tryGetReadOnlyRecord()
            }
            .toResult(true)
            .then { List<ReadOnlyRecord> rRecs ->
                trySend(EVENT_CONTACTS, Staffs.findEveryForRecordIds(rRecs*.id), wraps)
                    .logFail("sendIndividualWrappers: phone records `${wraps*.id}`")
            }
    }

    void sendFutureMessages(Collection<FutureMessage> fMsgs) {
        // refresh trigger so we get the most up-to-date job detail info
        fMsgs?.each { FutureMessage fMsg -> fMsg.refreshTrigger() }
        trySend(EVENT_FUTURE_MESSAGES, Staffs.findEveryForRecordIds(fMsgs*.record*.id), fMsgs)
            .logFail("sendFutureMessages: future messages `${fMsgs*.id}`")
    }

    void sendPhone(Phone p1) {
        trySend(EVENT_PHONES, p1.owner.buildAllStaff(), [p1])
            .logFail("sendPhone: phone `${p1.id}`")
    }

    // Helpers
    // -------

    protected Result<Void> trySend(String event, Collection<Staff> staffs, Collection<?> toSend) {
        ResultGroup<?> resGroup = new ResultGroup<>()
        if (staffs && toSend) {
            toSend.collate(PAYLOAD_BATCH_SIZE) // prevent exceeding payload max size
                .each { Collection<?> batch ->
                    Collection serialized = DataFormatUtils.jsonToObject(batch)
                    staffs.each { Staff s1 ->
                        resGroup << trySendToDataToStaff(event, s1, serialized)
                    }
                }
        }
        resGroup.toEmptyResult(false)
    }

    protected Result<Void> trySendToDataToStaff(String event, Staff s1, Object data) {
        try {
            String channelName = s1.channelName
            PusherResult pRes = pusherService.get("/channels/$channelName")
            if (pRes.status == PusherResult.Status.SUCCESS) {
                Map channelInfo = DataFormatUtils.jsonToObject(pRes.message) as Map
                if (channelInfo.occupied) {
                    pRes = pusherService.trigger(channelName, event, data)
                }
            }

            if (pRes.status == PusherResult.Status.SUCCESS) {
                IOCUtils.resultFactory.success()
            }
            else {
                log.error("trySendToDataToStaff: ${pRes.properties}")
                IOCUtils.resultFactory.failForPusher(pRes)
            }
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "trySendToDataToStaff", true)
        }
    }
}
