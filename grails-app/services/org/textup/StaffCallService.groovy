package org.textup

import grails.transaction.Transactional

@Transactional
class StaffCallService extends CallService {

    //////////////////////////
    // Incoming from client //
    //////////////////////////

    Result<Closure> handleIncoming(TransientPhoneNumber from, TransientPhoneNumber to, String apiId) {
        //if staff member is calling from personal phone to TextUp phone
        if (Staff.forPersonalAndWorkPhoneNums(from, to).count()) {
            twimlBuilder.buildXmlFor(CallResponse.SELF_GREETING)
        }
        else { super.connect(from, to, apiId) }
    }

    ////////////////////////
    // Incoming from self //
    ////////////////////////

    Result<Closure> handleIncomingDigitsFromSelf(String apiId, TransientPhoneNumber workNum, String digits) {
        Phone phone = Phone.forNumber(workNum).get()
        if (phone) {
            TransientPhoneNumber numberToCall = null,
                tNum = new TransientPhoneNumber(number:digits)
            //case 1: digits are a phone number
            if (tNum.validate()) { //then is a valid phone number
                Result res = recordService.createOutgoingRecordCall(phone, tNum, [apiId:apiId])
                if (res.success) {  numberToCall = tNum }
                else { log.debug("StaffService.handleIncomingDigitsFromSelf: ${res}") }
            }
            //case 2: digits are a contact id
            else if (numOrCode.isLong() && Contact.exists(numOrCode.toLong())) {
                Contact contact = Contact.forPhoneAndContactId(phone, numOrCode.toLong()).get()
                if (contact) {
                    TransientPhoneNumber to = TransientPhoneNumber.copy(contact.numbers[0])
                    Result res = recordService.createRecordCallForContact(contact.id, workNum, to,
                        null, [apiId:apiId])
                    if (res.success) {  numberToCall = to }
                    else { log.debug("StaffService.handleIncomingDigitsFromSelf: ${res}") }
                }
            }
            
            if (numberToCall) {
                twimlBuilder.buildXmlFor(CallResponse.SELF_CONNECTING, [num:numberToCall.number])
            }
            else { twimlBuilder.buildXmlFor(CallResponse.SELF_ERROR, [digits:digits]) }
        }
        else { twimlBuilder.buildXmlFor(CallResponse.SELF_ERROR, [digits:digits]) }
    }
}
