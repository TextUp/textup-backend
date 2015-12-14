package org.textup

import grails.transaction.Transactional

@Transactional
class TeamTextService extends TextService {

    Result<Closure> handleIncoming(TransientPhoneNumber from, TransientPhoneNumber to, 
        String apiId, String contents) {

        TeamPhone p1 = TeamPhone.forTeamNumber(to).get()
        if (!p1) { return twimlBuilder.buildXmlFor(TextResponse.NOT_FOUND) }
        ClientSession ts1 = ClientSession.findOrCreateForTeamPhoneAndNumber(p1, from)
        if (!ts1) { return twimlBuilder.buildXmlFor(TextResponse.SERVER_ERROR) }
        List<Contact> contacts = Contact.findOrCreateForPhoneAndNum(p1, from)
        if (!contacts) { return twimlBuilder.buildXmlFor(TextResponse.SERVER_ERROR) }

        contents = Helpers.cleanIncomingText(contents)
        switch (contents) {
            case Constants.ACTION_SEE_ANNOUNCEMENTS:
                return seeAnnouncements(p1.currentFeatures)
            case Constants.ACTION_SUBSCRIBE:
                return subscribeAll(p1, contacts)
            case Constants.ACTION_UNSUBSCRIBE_ALL:
                return unsubscribeAll(p1, contacts)
            case Constants.ACTION_UNSUBSCRIBE_ONE:
                return unsubscribeOne(ts1, contacts)
            default:
                return relayText(ts1, from, p1, contents, apiId)
        }
    }

    //////////////////////////////////
    // Incoming text helper methods //
    //////////////////////////////////

    protected Result<Closure> seeAnnouncements(List<FeaturedAnnouncement> features) {
        if (features) {
            twimlBuilder.buildXmlFor(TextResponse.TEAM_ANNOUNCEMENTS, [features:features])
        }
        else { twimlBuilder.buildXmlFor(TextResponse.TEAM_NO_ANNOUNCEMENTS) }
    }
    protected Result<Closure> subscribeAll(TeamPhone p1, List<Contact> contacts) {
        List<ContactTag> tags = p1.tags
        contacts.each { Contact c1 ->
            tags.each { ContactTag t1 -> c1.subscribeToTag(t1, Constants.SUBSCRIPTION_TEXT) }
        }
        twimlBuilder.buildXmlFor(TextResponse.TEAM_SUBSCRIBE_ALL)
    }
    protected Result<Closure> unsubscribeAll(TeamPhone p1, List<Contact> contacts) {
        List<ContactTag> tags = p1.tags
        contacts.each { Contact c1 ->
            tags.each { ContactTag t1 -> c1.quietUnsubscribeFromTag(t1) }
        }
        twimlBuilder.buildXmlFor(TextResponse.TEAM_UNSUBSCRIBE_ALL)
    }
    protected Result<Closure> unsubscribeOne(ClientSession ts1, List<Contact> contacts) {
        Result<Closure> res = twimlBuilder.noResponse()
        if (ts1.mostRecentTagId != null) {
            TeamContactTag ct1 = TeamContactTag.get(ts1.mostRecentTagId)
            if (ct1) {
                contacts.each { Contact c1 -> c1.quietUnsubscribeFromTag(ct1) }
                res = twimlBuilder.buildXmlFor(TextResponse.TEAM_UNSUBSCRIBE_ONE, [tagName:ct1.name])
            }
        }
        res
    }
    protected Result<Closure> relayText(ClientSession ts1, TransientPhoneNumber fromNum, TeamPhone p1,
        String contents, String apiId) {
        Result res = recordService.createIncomingRecordText(fromNum, p1, [contents:contents], [apiId:apiId])
        if (res.success) {
            if (ts1.shouldSendInstructions()) {
                ts1.updateLastSentInstructions()
                if (ts1.hasTextSubscriptions()) {
                    twimlBuilder.buildXmlFor(TextResponse.TEAM_INSTRUCTIONS)
                }
                else {
                    twimlBuilder.buildXmlFor(TextResponse.TEAM_INSTRUCTIONS_SUBSCRIBED)
                }
            }
            else { twimlBuilder.noResponse() }
        }
        else { twimlBuilder.buildXmlFor(TextResponse.NOT_FOUND) }
    }
}
