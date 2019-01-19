package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class CallbackUtils {

    static final String PARAM_HANDLE = "handle"
    static final String PARAM_CHILD_CALL_NUMBER = "childStatusNumber"
    static final String STATUS = "status"

    static boolean shouldUpdateStatus(ReceiptStatus oldStatus, ReceiptStatus newStatus) {
        !oldStatus && !newStatus ?
            false :
            !oldStatus || oldStatus.isEarlierInSequenceThan(newStatus)
    }

    static boolean shouldUpdateDuration(Integer oldDuration, Integer newDuration) {
        !oldDuration && !newDuration ?
            false :
            newDuration != null && (oldDuration == null || oldDuration != newDuration)
    }

    static Result<? extends BasePhoneNumber> tryGetNumberForSession(BasePhoneNumber fromNum,
        BasePhoneNumber toNum, TypeMap params) {

        CallResponse handle = params.enum(CallResponse, CallbackUtils.PARAM_HANDLE)
        // finish bridge is call from phone to personal phone
        // announcements are from phone to session (client)
        if (handle == CallResponse.FINISH_BRIDGE ||
            handle == CallResponse.ANNOUNCEMENT_AND_DIGITS ||
            handle == CallResponse.VOICEMAIL_GREETING_RECORD ||
            handle == CallResponse.VOICEMAIL_GREETING_PROCESSED ||
            handle == CallResponse.VOICEMAIL_GREETING_PLAY) {
            IOCUtils.resultFactory.success(toNum)
        }
        // when screening incoming calls, the From number is the TextUp phone,
        // the original caller is stored in the originalFrom parameter and the
        // To number is actually the staff member's personal phone number
        else if (handle == CallResponse.SCREEN_INCOMING) {
            CallTwiml.tryExtractScreenIncomingFrom(params)
        }
        //usually handle incoming from session (client) to phone (staff)
        else { IOCUtils.resultFactory.success(fromNum) }
    }

    static BasePhoneNumber numberForPhone(BasePhoneNumber fromNum, BasePhoneNumber toNum,
        TypeMap params) {

        CallResponse handle = params.enum(CallResponse, CallbackUtils.PARAM_HANDLE)
        //usually handle incoming from session (client) to phone (staff)
        BasePhoneNumber phoneNum = toNum
        // (1) finish bridge is call from phone to personal phone
        // (2) announcements are from phone to session (client)
        // (3) when screening incoming calls, the From number is the TextUp phone,
        //      the original caller is stored in the originalFrom parameter and the
        //      To number is actually the staff member's personal phone number
        // (4 + 5) recording voicemail greeting takes place when TextUp phone calls personal phone
        if (handle == CallResponse.FINISH_BRIDGE ||
            handle == CallResponse.ANNOUNCEMENT_AND_DIGITS ||
            handle == CallResponse.SCREEN_INCOMING ||
            handle == CallResponse.VOICEMAIL_GREETING_RECORD ||
            handle == CallResponse.VOICEMAIL_GREETING_PROCESSED ||
            handle == CallResponse.VOICEMAIL_GREETING_PLAY) {
            phoneNum = fromNum
        }
        phoneNum
    }
}
