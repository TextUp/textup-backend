package org.textup

import grails.transaction.Transactional

@Transactional
class TeamCallService extends CallService {

    //////////////////////////////
    // Incoming from any number //
    //////////////////////////////

    Result<Closure> handleIncoming(TransientPhoneNumber from, TransientPhoneNumber to, String apiId) {
        TeamPhone p1 = TeamPhone.forTeamNumber(to).get()
        Team t = p1 ? Team.forPhone(p1).get() : null
        if (p1 && t) {
            Result res = recordService.createIncomingRecordCall(from, p1, [apiId:apiId])
            if (res.success) {
                ClientSession ts1 = ClientSession.findOrCreateForTeamPhoneAndNumber(p1, from)
                if (ts1) {
                    twimlBuilder.buildXmlFor(CallResponse.TEAM_GREETING, 
                        [teamName:t.name, isSubscribed:ts1.hasCallSubscriptions()])
                }
                else { twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR) }
            }
            else { res }
        }
        else { twimlBuilder.buildXmlFor(CallResponse.DEST_NOT_FOUND, [num:to]) }
    }

    Result<Closure> handleIncomingDigits(TransientPhoneNumber from, TransientPhoneNumber to, String digits) {
        if (digits == Constants.CALL_GREETING_CONNECT_TO_STAFF) {
            return connect(from, to, params.CallSid)
        }
        TeamPhone phone = TeamPhone.forTeamNumber(to).get()
        if (phone) {
            ClientSession ts1 = ClientSession.findOrCreateForTeamPhoneAndNumber(p1, from)
            if (!ts1) { return twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR) }
            List<Contact> contacts = Contact.findOrCreateForPhoneAndNum(p1, from)
            if (!contacts) { return twimlBuilder.buildXmlFor(CallResponse.SERVER_ERROR) }
            List<ContactTag> tags = p1.tags
            //connect to staff case redirected in PublicRecordController
            Result res
            switch(digits) {
                case Constants.CALL_GREETING_HEAR_ANNOUNCEMENTS:
                    List<FeaturedAnnouncement> features = p1.currentFeatures
                    res = features ? 
                        twimlBuilder.buildXmlFor(CallResponse.TEAM_ANNOUNCEMENTS, [features:features]) :
                        twimlBuilder.buildXmlFor(CallResponse.TEAM_NO_ANNOUNCEMENTS)
                    break
                case Constants.CALL_GREETING_SUBSCRIBE_ALL:
                    contacts.each { Contact c1 ->
                        tags.each { ContactTag t1 -> c1.subscribeToTag(t1, Constants.SUBSCRIPTION_CALL) }
                    }
                    res = twimlBuilder.buildXmlFor(CallResponse.TEAM_SUBSCRIBE_ALL)
                    break
                case Constants.CALL_GREETING_UNSUBSCRIBE_ALL:
                    contacts.each { Contact c1 ->
                        tags.each { ContactTag t1 -> c1.quietUnsubscribeFromTag(t1) }
                    }
                    res = twimlBuilder.buildXmlFor(CallResponse.TEAM_UNSUBSCRIBE_ALL)
                    break
                default:
                    res = twimlBuilder.buildXmlFor(CallResponse.TEAM_ERROR, [digits:digits])
                    break
            }
            res 
        }
        else { twimlBuilder.buildXmlFor(CallResponse.DEST_NOT_FOUND, [num:to]) }
    }
    
    ////////////////////////////////////////////////
    // Outgoing call announcements to subscribers //
    ////////////////////////////////////////////////

    Result<RecordCall> startCallAnnouncement(Phone fromPhone, Contactable toContact, RecordCall call, Long teamContactTagId, Long recordTextId) {
        if (call.validate()) {
            tryCall(toContact.numbers[0]?.e164PhoneNumber, call, fromPhone.number.e164PhoneNumber, toContact,
                [handle:Constants.CALL_ANNOUNCEMENT, teamContactTagId:teamContactTagId, recordTextId:recordTextId])
        }
        else { resultFactory.failWithValidationErrors(call.error) }
    }
    Result<Closure> completeCallAnnouncement(Long teamContactTagId, Long recordTextId) {
        RecordText rt1 = RecordText.get(recordTextId)
        TeamContactTag ct1 = TeamContactTag.get(teamContactTagId)
        if (rt1 && ct1) {
            Team t1 = Team.forPhone(ct1.phone).get()
            twimlBuilder.buildXmlFor(CallResponse.ANNOUNCEMENT,
                [contents:rt1.contents, teamName:t1.name, tagName:ct1.name, tagId:ct1.id, textId:rt1.id])
        }
        else {
            log.error("CallService.completeCallAnnouncement: RecordText ${recordTextId} not found.")
            resultFactory.failWithMessageAndStatus(NOT_FOUND, "callService.completeCallAnnouncement.notFound", 
                [rt1?.id, ct1?.id])
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // Digits when subscriber wants to unsubscribe from call announcement //
    ////////////////////////////////////////////////////////////////////////
    
    Result<Closure> handleAnnouncementDigits(TransientPhoneNumber contactNum, TransientPhoneNumber phoneNum, 
        String digits, Long teamContactTagId, Long recordTextId) {
        if (digits == Constants.CALL_ANNOUNCE_UNSUBSCRIBE_ONE) {
            handleCallAnnouncementUnsubscribeOne(contactNum, phoneNum, teamContactTagId)
        }
        else if (digits == Constants.CALL_ANNOUNCE_UNSUBSCRIBE_ALL) {
            handleCallAnnouncementUnsubscribeAll(contactNum, phoneNum)
        }
        else { completeCallAnnouncement(teamContactTagId, recordTextId) }
    }

    protected Result<Closure> handleCallAnnouncementUnsubscribeOne(TransientPhoneNumber contactNum, TransientPhoneNumber phoneNum, Long teamContactTagId) {
        
        TeamPhone p1 = TeamPhone.forTeamNumber(phoneNum)
        TeamContactTag ct1 = TeamContactTag.get(teamContactTagId)
        if (p1 && ct1) {
            List<Contact> contacts = Contact.forPhoneAndNum(p1, contactNum).list()
            contacts.each { Contact c1 -> c1.quietUnsubscribeFromTag(ct1) }
            twimlBuilder.buildXmlFor(CallResponse.ANNOUNCEMENT_UNSUBSCRIBE_ONE, [tagName:ct1.name])
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND, "callService.handleCallAnnouncementUnsubscribeOne.notFound", [p1?.id, ct1?.id])
        }
    }
    protected Result<Closure> handleCallAnnouncementUnsubscribeAll(TransientPhoneNumber contactNum, TransientPhoneNumber phoneNum) {
        TeamPhone p1 = TeamPhone.forTeamNumber(phoneNum)
        if (p1) {
            List<Contact> contacts = Contact.forPhoneAndNum(p1, contactNum).list()
            List<ContactTag> tags = p1.tags
            contacts.each { Contact c1 ->
                tags.each { ContactTag t1 -> c1.quietUnsubscribeFromTag(t1) }
            }
            twimlBuilder.buildXmlFor(CallResponse.ANNOUNCEMENT_UNSUBSCRIBE_ALL)
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND, "callService.handleCallAnnouncementUnsubscribeAll.notFound", [p1?.id])
        }
    }
}
