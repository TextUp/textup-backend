package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*
import com.twilio.sdk.resource.instance.IncomingPhoneNumber
import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.TwilioRestException
import org.apache.http.message.BasicNameValuePair
import org.apache.http.NameValuePair

@Transactional
class StaffService {

    def resultFactory
    def authService
    def mailService
    def grailsApplication

    //////////////////
    // REST methods //
    //////////////////

    Result<Staff> create(Map body) {
    	Staff s1 = new Staff()
    	s1.with {
    		username = body.username
    		password = body.password
    		name = body.name
    		email = body.email
    		if (body.manualSchedule) manualSchedule = body.manualSchedule
    		if (body.isAvailable) isAvailable = body.isAvailable
            //NOT allowed to change status away from default 'pending'
		}
		if (body.org) {
            Organization org
			def o = body.org
            //if we specify id then we must be associating with existing
            if (o.id) {
                s1.status = Constants.STATUS_PENDING
                org = Organization.get(o.id)
                if (!org) {
                    return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                        "staffService.create.orgNotFound", [o.id])
                }
                Result res = mailService.notifyAdminsOfPendingStaff(s1.name, org.admins)
                if (!res.success) { return res }
            }
            else {
                //We will manually change this staff status to ADMIN
                //after we approve the organization
                s1.status = Constants.STATUS_ADMIN
                org = new Organization(o)
                org.status = Constants.ORG_PENDING
                org.location = new Location(o.location)
                if (!org.location.save()) { //needs to be before org.save()
                    return resultFactory.failWithValidationErrors(org.location.errors)
                }
                if (!org.save()) {
                    return resultFactory.failWithValidationErrors(org.errors)
                }
                Result res = mailService.notifySuperOfNewOrganization(org.name)
                if (!res.success) { return res }
            }
			s1.org = org
		}
		if (s1.save()) { resultFactory.success(s1) }
    	else { resultFactory.failWithValidationErrors(s1.errors) }
    }

    Result<Staff> update(Long staffId, Map body) {
    	Staff s1 = Staff.get(staffId)
    	if (!s1) {
    		return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "staffService.update.notFound", [staffId])
    	}
    	s1.with {
    		if (body.name) name = body.name
            if (body.username) username = body.username
            if (body.password) password = body.password
            if (body.email) email = body.email
            if (body.manualSchedule) manualSchedule = body.manualSchedule
            if (body.isAvailable) isAvailable = body.isAvailable
            if (body.awayMessage) awayMessage = body.awayMessage
		}
        if (body.personalPhoneNumber) {
            s1.personalPhoneNumberAsString = body.personalPhoneNumber
        }
		if (body.schedule) {
			Result res = s1.schedule.updateWithIntervalStrings(body.schedule)
			if (!res.success) { return res }
		}
		if (body.phone || body.phoneId) {
            StaffPhone p1 = s1.phone ?: new StaffPhone()
            def twilioConfig = grailsApplication.config.textup.apiKeys.twilio
            //only proceed if we are trying to CHANGE our phone number
            if (!((body.phone && Helpers.cleanNumber(body.phone) == p1.numberAsString) ||
                (body.phoneId && body.phoneId == p1.apiId))) {
                try {
                    TwilioRestClient client = new TwilioRestClient(twilioConfig.sid, twilioConfig.authToken)
                    List<NameValuePair> params = []
                    params.with {
                        add(new BasicNameValuePair("FriendlyName", twilioConfig.unavailable))
                        add(new BasicNameValuePair("SmsApplicationSid", twilioConfig.appId))
                        add(new BasicNameValuePair("VoiceApplicationSid", twilioConfig.appId))
                    }
                    IncomingPhoneNumber currNum = p1.apiId ? client.account.getIncomingPhoneNumber(p1.apiId) : null,
                        newNum
                    if (body.phoneId) {
                        newNum = client.account.getIncomingPhoneNumber(body.phoneId)
                        newNum.update(params)
                    }
                    else {
                        params.add(new BasicNameValuePair("PhoneNumber", body.phone))
                        newNum = client.account.incomingPhoneNumberFactory.create(params)
                    }
                    //update staff phone
                    if (currNum) {
                        currNum.update([new BasicNameValuePair("FriendlyName", twilioConfig.available)])
                    }
                    p1.apiId = newNum.sid
                    p1.numberAsString = newNum.phoneNumber
                }
                catch (TwilioRestException e) {
                    return resultFactory.failWithThrowable(e)
                }
            }
            s1.phone = p1
            if (!s1.phone.save()) {
                return resultFactory.failWithValidationErrors(s1.phone.errors)
            }
		}
        if (body.status) { //can only update status if you are an admin
            if (authService.isAdminAtSameOrgAs(s1.id)) {
                String oldStatus = s1.status
                s1.status = body.status
                if (oldStatus == Constants.STATUS_PENDING &&
                    s1.status != Constants.STATUS_PENDING && s1.save()) {
                    Result res
                    if (s1.status == Constants.STATUS_ADMIN ||
                        s1.status == Constants.STATUS_STAFF) {
                        res = mailService.notifyPendingOfApproval(s1)
                    }
                    else {
                        res = mailService.notifyPendingOfRejection(s1)
                    }
                    if (!res.success) { return res }
                }
            }
            else {
                s1.discard()
                return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                    "staffService.update.statusNotAdmin", [staffId])
            }
        }
		if (s1.save()) { resultFactory.success(s1) }
    	else { resultFactory.failWithValidationErrors(s1.errors) }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    boolean staffExistsForPersonalAndWorkPhoneNums(String personalNum, String workNum) {
        Staff.forPersonalAndWorkPhoneNums(personalNum, workNum).get() != null
    }
}
