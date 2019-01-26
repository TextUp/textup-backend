package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.validator.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
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
        new NotificationInfo(notif1.mutablePhone.owner.buildName(),
            notif1.mutablePhone.number,
            notif1.countVoicemails(op1),
            notif1.countItems(false, op1, RecordText),
            notif1.countItems(false, op1, RecordCall),
            NotificationUtils.buildPublicNames(notif1, false),
            notif1.countItems(true, op1, RecordText),
            notif1.countItems(true, op1, RecordCall),
            NotificationUtils.buildPublicNames(notif1, true))
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
