package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*
import com.twilio.sdk.resource.instance.IncomingPhoneNumber
import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.TwilioRestException
import org.apache.http.message.BasicNameValuePair
import org.apache.http.NameValuePair
import com.twilio.sdk.resource.instance.Account

@Transactional
class StaffService {

    def resultFactory
    def authService
    def mailService
    def twilioService
    def grailsApplication

    // Create
    // ------

    Result<Staff> create(Map body) {
    	Staff s1 = new Staff()
    	s1.with {
    		username = body.username
    		password = body.password
    		name = body.name
    		email = body.email
    		if (body.manualSchedule) manualSchedule = body.manualSchedule
    		if (body.isAvailable) isAvailable = body.isAvailable
		}
        this.addStaffToOrg(s1, body.org).then({ Organization o1 ->
            // only allowed to change status if is admin
            if (body.status && o1.id && authService.isAdminAt(o1.id)) {
                s1.status = Helpers.convertEnum(StaffStatus, body.status)
            }
            if (s1.save(flush:true)) {
                StaffRole.create(s1, Role.findByAuthority("ROLE_USER"), true)
                resultFactory.success(s1)
            }
            else { resultFactory.failWithValidationErrors(s1.errors) }
        })
    }
    protected Result<Organization> addStaffToOrg(Staff s1, Map orgInfo) {
        if (!orgInfo) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "staffService.create.mustSpecifyOrg")
        }
        Organization org
        //if we specify id then we must be associating with existing
        if (orgInfo.id) { // existing organization
            org = Organization.get(orgInfo.id)
            if (!org) {
                return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                    "staffService.create.orgNotFound", [orgInfo.id])
            }
            //if logged in is admin at org we are adding this staff to, permit status update
            s1.status = StaffStatus.PENDING
            Result res = mailService.notifyAdminsOfPendingStaff(s1.name, org.admins)
            if (!res.success) { return res }
        }
        else { // create new organization
            s1.status = StaffStatus.ADMIN
            org = new Organization(orgInfo)
            org.status = OrgStatus.PENDING
            org.location = new Location(orgInfo.location)
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

        resultFactory.success(org)
    }

    // Update
    // ------

    Result<Staff> update(Long staffId, Map body, String timezone) {
        Result.<Staff>waterfall(
            this.&findStaffForId.curry(staffId),
            this.&updateStaff.rcurry(body, timezone),
            this.&updatePhoneNumber.rcurry(body)
        ).then({ Staff s1 ->
            if (s1.save()) {
                resultFactory.success(s1)
            }
            else { resultFactory.failWithValidationErrors(s1.errors) }
        })
    }
    protected Result<Staff> findStaffForId(Long sId) {
        Staff s1 = Staff.get(sId)
        s1 ? resultFactory.success(s1) : resultFactory.failWithMessageAndStatus(NOT_FOUND,
            "staffService.update.notFound", [sId])
    }
    protected Result<Staff> updateStaff(Staff s1, Map body, String timezone) {
        s1.with {
            if (body.name) name = body.name
            if (body.username) username = body.username
            if (body.password) password = body.password
            if (body.email) email = body.email
            if (body.manualSchedule) manualSchedule = body.manualSchedule
            if (body.isAvailable) isAvailable = body.isAvailable
            if (body.personalPhoneNumber) {
                personalPhoneNumberAsString = body.personalPhoneNumber
            }
        }
        if (s1.phone && body.awayMessage) {
            s1.phone.awayMessage = body.awayMessage
            if (!s1.phone.save()) {
                return resultFactory.failWithValidationErrors(s1.phone.errors)
            }
        }
        if (body.schedule) {
            Result res = s1.schedule.updateWithIntervalStrings(body.schedule, timezone)
            if (!res.success) { return res }
        }
        //can only update status if you are an admin
        if (body.status && authService.isAdminAtSameOrgAs(s1.id)) {
            StaffStatus oldStatus = s1.status
            s1.status = Helpers.convertEnum(StaffStatus, body.status)
            if (!s1.validate()) {
                return resultFactory.failWithValidationErrors(s1.errors)
            }
            // email notifications if changing away from pending
            if (oldStatus.isPending && !s1.status.isPending) {
                Result res = s1.status.isActive ?
                    mailService.notifyPendingOfApproval(s1) :
                    mailService.notifyPendingOfRejection(s1)
                if (!res.success) { return res }
            }
        }
        resultFactory.success(s1)
    }
    protected Result<Staff> updatePhoneNumber(Staff s1, Map body) {
        if (body.phone || body.phoneId) {
            Phone p1 = s1.phone ?: new Phone()
            s1.phone = p1
            PhoneNumber pNum = new PhoneNumber(number:body.phone)
            // only proceed if we are trying to CHANGE our phone number
            if ((pNum.validate() && pNum.number == p1.numberAsString) ||
                (body.phoneId && body.phoneId == p1.apiId)) {
                Result<IncomingPhoneNumber> res = this.changeNumber(s1, p1, pNum, body.phoneId)
                if (!res.success) { return res }
                IncomingPhoneNumber newNum = res.payload
                p1.apiId = newNum.sid
                p1.numberAsString = newNum.phoneNumber
                if (!p1.save()) {
                    return resultFactory.failWithValidationErrors(p1.errors)
                }
            }
        }
        resultFactory.success(s1)
    }
    protected Result<IncomingPhoneNumber> changeNumber(Staff s1, Phone p1,
        PhoneNumber pNum, String apiId) {
        def tConfig = grailsApplication.config.textup.apiKeys.twilio
        try {
            Account ac = twilioService.account
            List<NameValuePair> params = []
            params.with {
                add(new BasicNameValuePair("FriendlyName", tConfig.unavailable))
                add(new BasicNameValuePair("SmsApplicationSid", tConfig.appId))
                add(new BasicNameValuePair("VoiceApplicationSid", tConfig.appId))
            }
            IncomingPhoneNumber currNum = p1.apiId ? ac.getIncomingPhoneNumber(p1.apiId) : null
            IncomingPhoneNumber newNum
            if (apiId) { // update number that we already own
                newNum = ac.getIncomingPhoneNumber(apiId)
                newNum.update(params)
            }
            else { // purchase this new number
                params.add(new BasicNameValuePair("PhoneNumber", pNum?.e164PhoneNumber))
                newNum = ac.incomingPhoneNumberFactory.create(params)
            }
            if (currNum) { // update previous number to make available
                currNum.update([new BasicNameValuePair("FriendlyName", tConfig.available)])
            }
            resultFactory.success(newNum)
        }
        catch (TwilioRestException e) {
            return resultFactory.failWithThrowable(e)
        }
    }
}
