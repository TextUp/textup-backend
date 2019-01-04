package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingNotificationService {

    GrailsApplication grailsApplication
    TextService textService
    TokenService tokenService

    // TODO add notification type here?
    ResultGroup<Void> send(List<OutgoingNotification> notifs, Boolean outgoing, String contents) {
        // // TODO restore
        // ResultGroup<Void> outcomes = new ResultGroup<>()
        // notifs.each { OutgoingNotification bn1 ->
        //     outcomes << sendForNotification(bn1, outgoing, contents)
        // }
        // outcomes.logFail("NotificationService.send for records with ids: ${notifs*.record*.id}")
    }

    protected Result<Void> sendForNotification(OutgoingNotification bn1, Boolean isOut, String msg1) {
        // TODO restore
        // Phone p1 = bn1.owner.phone
        // Staff s1 = bn1.staff
        // // short circuit if no staff specified or staff has no personal phone
        // if (!s1?.personalPhoneAsString) {
        //     return IOCUtils.resultFactory.success()
        // }
        // Map tokenData = [
        //     phoneId: p1.id,
        //     recordId: bn1.record.id,
        //     contents: msg1,
        //     outgoing: isOut
        // ]
        // String notifyLink = grailsApplication.flatConfig["textup.links.notifyMessage"],
        //     suffix = IOCUtils.getMessage("notificationService.send.notificationSuffix"),
        //     instr = buildInstructions(bn1, isOut)
        // // Surround the link with messages to prevent iMessage from removing the link from the message
        // // in order to generate a preview
        // tokenService.generateNotification(tokenData).then { Token tok1 ->
        //     String notification = "${instr} \n\n${notifyLink + tok1.token} \n\n${suffix}"
        //     textService.send(p1.number, [s1.personalPhoneNumber], notification, p1.customAccountId)
        // }
    }

    // Outgoing notification is always OutgoingNotification and never Notification. Notification objects
    // are only created when the preview message token is redeemed
    protected String buildInstructions(OutgoingNotification bn1, Boolean isOutgoing) {
        // // TODO
        // String ownerName = bn1.owner.buildName(),
        //     otherInitials = StringUtils.buildInitials(bn1.otherName)
        // if (otherInitials) {
        //     String code = isOutgoing
        //         ? "notificationService.outgoing.withFrom"
        //         : "notificationService.incoming.withFrom"
        //     IOCUtils.getMessage(code, [ownerName, otherInitials])
        // }
        // else {
        //     String code = isOutgoing
        //         ? "notificationService.outgoing.noFrom"
        //         : "notificationService.incoming.noFrom"
        //     IOCUtils.getMessage(code, [ownerName])
        // }
    }
}
