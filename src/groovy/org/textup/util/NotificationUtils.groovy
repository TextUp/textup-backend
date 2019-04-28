package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class NotificationUtils {

    static Result<NotificationGroup> tryBuildNotificationGroup(Collection<? extends RecordItem> rItems1) {
        NotificationUtils.tryBuildNotificationsForItems(rItems1)
            .then { List<Notification> notifs -> NotificationGroup.tryCreate(notifs) }
    }

    static Result<List<Notification>> tryBuildNotificationsForItems(Collection<? extends RecordItem> rItems1,
        Collection<Long> phoneIds = null) {

        if (!rItems1) {
            return IOCUtils.resultFactory.success([])
        }
        // Seems to not compile if we use MapUtils.buildManyUniqueObjectsMap
        Map<Long, Collection<? extends RecordItem>> recIdToItems = [:]
            .withDefault { [] as Collection<? extends RecordItem> }
        rItems1.each { RecordItem rItem1 -> recIdToItems[rItem1.record.id] << rItem1 }
        // Do not want to notify non-visible (e.g., blocked) `PhoneRecord`s
        List<PhoneRecord> prs = PhoneRecords
            .buildActiveForRecordIds(recIdToItems.keySet())
            .build(PhoneRecords.forVisibleStatuses())
            .build(PhoneRecords.forPhoneIds(phoneIds, true)) // optional
            .list()
        Map<Phone, Notification> mutPhoneToNotif = [:]
        ResultGroup
            .collect(prs) { PhoneRecord pr1 ->
                PhoneRecordWrapper w1 = pr1.toWrapper()
                // NOT original phone so that we notify shared staff too
                w1.tryGetMutablePhone()
                    .then { Phone p1 -> Notification.tryCreate(p1) }
                    .then { Notification fallbackNotif ->
                        if (!mutPhoneToNotif.containsKey(fallbackNotif.mutablePhone)) {
                            mutPhoneToNotif[fallbackNotif.mutablePhone] = fallbackNotif
                        }
                        NotificationDetail.tryCreate(w1)
                            .curry(mutPhoneToNotif[fallbackNotif.mutablePhone])
                    }
                    .then { Notification notif1, NotificationDetail nd1 ->
                        // only notify those that has modify permissions or higher
                        w1.tryGetRecord().curry(notif1, nd1)
                    }
                    .then { Notification notif1, NotificationDetail nd1, Record rec1 ->
                        Collection<? extends RecordItem> rItems2 = recIdToItems[rec1.id]
                        if (rItems2) {
                            nd1.items.addAll(rItems2)
                        }
                        notif1.addDetail(nd1)
                        DomainUtils.tryValidate(notif1)
                    }
            }
            // allow some failures because we find shared phone records too. This includes
            // shared collaborators (who we want to notify) and those that are view-only, who we
            // do NOT want to notify
            .toEmptyResult(true)
            .then {
                IOCUtils.resultFactory.success(CollectionUtils.shallowCopyNoNull(mutPhoneToNotif.values()))
            }
    }

    static String buildPublicNames(Notification notif1, boolean isOut) {
        List<String> initials = WrapperUtils
            .publicNamesIgnoreFails(notif1?.getWrappersForOutgoing(isOut))
            .toList()
        CollectionUtils.joinWithDifferentLast(initials, ", ", " and ")
    }

    static String buildTextMessage(NotificationFrequency freq1, NotificationInfo n1) {
        List<String> parts = []
        if (n1) {
            parts << IOCUtils.getMessage("notificationInfo.context",
                [NotificationFrequency.descriptionWithFallback(freq1), n1.phoneName])
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
        }
        parts.join(" ")
    }

    static String buildIncomingMessage(NotificationInfo n1) {
        List<String> parts = [],
            countParts = []
        if (n1?.numIncomingText) {
            countParts << StringUtils.pluralize(n1.numIncomingText,
                IOCUtils.getMessage("notificationInfo.text"))
        }
        if (n1?.numIncomingCall) {
            countParts << StringUtils.pluralize(n1.numIncomingCall,
                IOCUtils.getMessage("notificationInfo.call"))
        }
        if (n1?.numVoicemail) {
            countParts << StringUtils.pluralize(n1.numVoicemail,
                IOCUtils.getMessage("notificationInfo.voicemail"))
        }
        if (countParts) {
            parts << countParts.join(", ")

            if (n1?.incomingNames) {
                parts << IOCUtils.getMessage("notificationInfo.from")
                parts << n1.incomingNames
            }
        }
        parts.join(" ")
    }

    static String buildOutgoingMessage(NotificationInfo n1) {
        List<String> parts = [],
            countParts = []
        if (n1?.numOutgoingText) {
            countParts << StringUtils.pluralize(n1.numOutgoingText,
                IOCUtils.getMessage("notificationInfo.scheduledText"))
        }
        if (n1?.numOutgoingCall) {
            countParts << StringUtils.pluralize(n1.numOutgoingCall,
                IOCUtils.getMessage("notificationInfo.scheduledCall"))
        }
        if (countParts) {
            parts << countParts.join(", ")

            if (n1?.outgoingNames) {
                parts << IOCUtils.getMessage("notificationInfo.to")
                parts << n1.outgoingNames
            }
        }
        parts.join(" ")
    }
}
