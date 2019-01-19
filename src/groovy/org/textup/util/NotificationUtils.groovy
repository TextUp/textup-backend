package org.textup.util

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class NotificationUtils {

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Result<NotificationGroup> tryBuildNotificationGroup(Collection<RecordItem> rItems1) {
        NotificationUtils.tryBuildNotificationsForItems(rItems1)
            .then { List<Notification> notifs -> NotificationGroup.tryCreate(notifs) }
    }

    static Result<List<Notification>> tryBuildNotificationsForItems(Collection<RecordItem> rItems1,
        Collection<Long> phoneIds = null) {

        Map<Long, Collection<RecordItem>> recIdToItems = MapUtils
            .buildManyObjectsMap(rItems1) { RecordItem rItem1 -> rItem1.record.id }
        List<PhoneRecord> prs = PhoneRecords
            .buildActiveForRecordIds(recIdToItems.keySet())
            .build(PhoneRecords.forPhoneIds(phoneIds, true)) // optional
            .list()
        RecordGroup
            .collect(prs) { PhoneRecord pr1 ->
                PhoneRecordWrapper w1 = pr1.toWrapper()
                w1.tryGetRecord()
                    .then { Record rec1 -> w1.tryGetPhone().curry(rec1) }
                    .then { Record rec1, Phone p1 ->
                        ResultGroup
                            .collect(recIdToItems[rec1.id]) { Collection<RecordItem> rItems2 ->
                                NotificationDetail.tryCreate(w1)
                                    .then { NotificationDetail nd1 ->
                                        nd1.addAll(rItems2)
                                        DomainUtils.tryValidate(nd1)
                                    }
                            }
                            .toResult(false)
                            .curry(p1)
                    }
                    .then { Phone p1, List<NotificationDetail> notifDetails ->
                        Notification.tryCreate(p1).curry(notifDetails)
                    }
                    .then { List<NotificationDetail> notifDetails, Notification notif1 ->
                        notifDetails.each { NotificationDetail nd1 -> notif1.addDetail(nd1) }
                        DomainUtils.tryValidate(notif1)
                    }
            }
            .toResult(false)
    }

    static String buildPublicNames(Notification notif1, boolean isOut) {
        Collection<String> initials = ResultGroup
            .collect(notif1.getWrappersForOutgoing(isOut)) { PhoneWrapper w1 ->
                w1.tryGetPublicName()
            }
            .payload
        CollectionUtils.joinWithDifferentLast(initials, ", ", " and ")
    }

    static String buildTextMessage(NotificationInfo n1) {
        List<String> parts = [n1.phoneName]

        String incoming = NotificationUtils.buildIncomingMessage(n1)
        if (incoming) {
            parts << IOCUtils.getMessage("notificationInfo.received")
            parts << incoming
        }

        String outgoing = NotificationUtils.buildOutgoingMessage(n1)
        if (outgoing) {
            if (incoming) {
                parts << IOCUtils.getMessage("notificationInfo.and")
            }
            parts << IOCUtils.getMessage("notificationInfo.sent")
            parts << outgoing
        }

        parts.join(" ")
    }

    static String buildIncomingMessage(NotificationInfo n1) {
        List<String> parts = [],
            countParts = []
        // incoming messages
        if (n1.numIncomingText) {
            countParts << StringUtils.withUnits(n1.numIncomingText,
                IOCUtils.getMessage("notificationInfo.text"))
        }
        if (n1.numIncomingCall) {
            countParts << StringUtils.withUnits(n1.numIncomingCall,
                IOCUtils.getMessage("notificationInfo.call"))
        }
        if (n1.numVoicemail) {
            countParts << StringUtils.withUnits(n1.numVoicemail,
                IOCUtils.getMessage("notificationInfo.voicemail"))
        }
        if (countParts) {
            parts << countParts.join(", ")

            if (n1.incomingNames) {
                parts << IOCUtils.getMessage("notificationInfo.from")
                parts << n1.incomingNames
            }
        }
        parts.join(" ")
    }

    static String buildOutgoingMessage(NotificationInfo n1) {
        List<String> parts = [],
            countParts = []
        if (n1.numOutgoingText) {
            countParts << StringUtils.withUnits(n1.numOutgoingText,
                IOCUtils.getMessage("notificationInfo.scheduledText"))
        }
        if (n1.numOutgoingCall) {
            countParts << StringUtils.withUnits(n1.numOutgoingCall,
                IOCUtils.getMessage("notificationInfo.scheduledCall"))
        }
        if (countParts) {
            parts << countParts.join(", ")

            if (n1.outgoingNames) {
                parts << IOCUtils.getMessage("notificationInfo.to")
                parts << n1.outgoingNames
            }
        }
        parts.join(" ")
    }
}
