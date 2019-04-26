package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

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

    static NotificationInfo create(ReadOnlyOwnerPolicy rop1, Notification notif1) {
        if (rop1 && notif1) {
            new NotificationInfo(notif1.mutablePhone.owner.buildName(),
                notif1.mutablePhone.number,
                notif1.countVoicemails(rop1),
                notif1.countItems(false, rop1, RecordText),
                notif1.countItems(false, rop1, RecordCall),
                NotificationUtils.buildPublicNames(notif1, false),
                notif1.countItems(true, rop1, RecordText),
                notif1.countItems(true, rop1, RecordCall),
                NotificationUtils.buildPublicNames(notif1, true))
        }
        else { new NotificationInfo() }
    }

    // Methods
    // -------

    String buildTextMessage(NotificationFrequency freq1, Token tok1 = null) {
        List<String> parts = [NotificationUtils.buildTextMessage(freq1, this)]
        if (tok1) {
            parts << LinkUtils.notification(tok1.token)
            parts << IOCUtils.getMessage("notificationInfo.previewLink")
        }
        parts.join("\n\n")
    }
}
