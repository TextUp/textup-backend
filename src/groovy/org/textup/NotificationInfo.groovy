package org.textup

import grails.compiler.GrailsTypeChecked
import org.textup.validator.*
import org.textup.util.*

@GrailsTypeChecked
class NotificationInfo {

    final String phoneName
    final PhoneNumber phoneNumber

    final int numVoicemail
    final int numIncomingText
    final int numIncomingCall
    final String incomingNames

    final int numOutgoingText
    final int numOutgoingCall
    final String outgoingNames

    static NotificationInfo create(OwnerPolicy op1, Notification notif1) {
        new NotificationInfo(phoneName: notif1.mutablePhone.owner.buildName(),
            phoneNumber: notif1.mutablePhone.number,
            numVoicemail: notif1.countVoicemails(op1),
            numIncomingText: notif1.countItems(false, op1, RecordText),
            numIncomingCall: notif1.countItems(false, op1, RecordCall),
            incomingNames: NotificationUtils.buildPublicNames(notif1, false),
            numOutgoingText: notif1.countItems(true, op1, RecordText),
            numOutgoingCall: notif1.countItems(true, op1, RecordCall),
            outgoingNames: NotificationUtils.buildPublicNames(notif1, true))
    }

    // Methods
    // -------

    String buildTextMessage(Token tok1 = null) {
        List<String> parts = [NotificationUtils.buildTextMessage(this)]
        if (tok1) {
            parts << LinkUtils.notification(tok1.token)
            parts << IOCUtils.getMessage("notificationInfo.previewLink")
        }
        parts.join("\n\n")
    }
}
