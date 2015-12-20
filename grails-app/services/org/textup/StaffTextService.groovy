package org.textup

import grails.transaction.Transactional

@Transactional
class StaffTextService extends TextService {

    Result<Closure> handleIncoming(TransientPhoneNumber from, TransientPhoneNumber to,
        String apiId, String contents) {
        //case 1: staff member is texting from personal phone to TextUp phone
        Staff s1 = Staff.forPersonalAndWorkPhoneNums(from, to).get()
        if (s1) {
            twimlBuilder.buildXmlFor(TextResponse.STAFF_SELF_GREETING, [staff:s1])
        }
        //case 2: someone is texting a TextUp phone
        else {
            StaffPhone p1 = StaffPhone.forStaffNumber(to).get()
            if (!p1) { return twimlBuilder.buildXmlFor(TextResponse.NOT_FOUND) }
            Result res = recordService.createIncomingRecordText(from, p1, [contents:contents], [apiId:apiId])
            if (res.success) {
                Staff s2 = Staff.get(p1.ownerId)
                if (s2?.isAvailableNow()) {
                    Contact c1 = Contact.forRecord(res.payload[0]?.record).get()
                    Result nRes = notifyStaff(s2, c1, from.number, contents)
                    if (!nRes.success) { log.error(nRes.payload.message) }
                    twimlBuilder.noResponse()
                }
                else { twimlBuilder.buildMessageFor(s2.awayMessage) }
            }
            else { res }
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    protected Result notifyStaff(Staff s1, Contact contact, String incomingFrom, String contents) {
        if (contact) {
            String attribution = contact.name ?: incomingFrom,
                to = s1.personalPhoneNumber?.e164PhoneNumber,
                from = s1.phone?.number?.e164PhoneNumber
            if (attribution && to && from) {
                textOnly(from, to, Helpers.formatTextNotification(attribution, contents))
            }
            else {
                resultFactory.failWithMessage("textService.notifyStaff.missingInfo", [attribution, to, from])
            }
        }
        else {
            resultFactory.failWithMessage("textService.notifyStaff.noContact", [s1.id])
        }
    }
}
