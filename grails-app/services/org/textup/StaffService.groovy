package org.textup

import com.twilio.sdk.resource.instance.IncomingPhoneNumber
import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.TwilioRestException
import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.types.OrgStatus
import org.textup.types.StaffStatus
import org.textup.validator.PhoneNumber
import static org.springframework.http.HttpStatus.*

@GrailsTypeChecked
@Transactional
class StaffService {

    ResultFactory resultFactory
    AuthService authService
    MailService mailService
    PhoneService phoneService

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
        if (body.personalPhoneNumber) {
            s1.personalPhoneNumber = new PhoneNumber(number:
                body.personalPhoneNumber as String)
        }
        addStaffToOrg(s1, body.org).then({ Organization o1 ->
            // only allowed to change status if is admin
            if (body.status && o1.id && authService.isAdminAt(o1.id)) {
                s1.status = Helpers.convertEnum(StaffStatus, body.status)
            }
            if (s1.save()) {
                Role role = Role.findOrCreateByAuthority("ROLE_USER")
                if (!role.save()) {
                    return resultFactory.failWithValidationErrors(role.errors)
                }
                StaffRole sRole = new StaffRole(staff:s1, role:role)
                if (sRole.save()) {
                    resultFactory.success(s1)
                }
                else { resultFactory.failWithValidationErrors(sRole.errors) }
            }
            else { resultFactory.failWithValidationErrors(s1.errors) }
        }) as Result
    }
    protected Result<Organization> addStaffToOrg(Staff s1, def rawInfo) {
        if (!rawInfo || !(rawInfo instanceof Map)) {
            return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "staffService.create.mustSpecifyOrg")
        }
        Map orgInfo = rawInfo as Map
        Organization org
        //if we specify id then we must be associating with existing
        if (orgInfo.id) { // existing organization
            org = Organization.get(Helpers.toLong(orgInfo.id))
            if (!org) {
                return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                    "staffService.create.orgNotFound", [orgInfo.id])
            }
            //if logged in is admin at org we are adding this staff to, permit status update
            s1.status = StaffStatus.PENDING
            Result res = mailService.notifyAdminsOfPendingStaff(s1.name, org.getAdmins())
            if (!res.success) { return res }
        }
        else { // create new organization
            s1.status = StaffStatus.ADMIN
            org = new Organization(orgInfo as Map)
            org.status = OrgStatus.PENDING
            org.location = new Location(orgInfo.location as Map)
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
            this.&updateStaffInfo.rcurry(body, timezone),
            this.&updatePhoneNumber.rcurry(body)
        ).then({ Staff s1 ->
            if (s1.save()) {
                resultFactory.success(s1)
            }
            else { resultFactory.failWithValidationErrors(s1.errors) }
        }) as Result
    }
    protected Result<Staff> findStaffForId(Long sId) {
        Staff s1 = Staff.get(sId)
        s1 ? resultFactory.success(s1) : resultFactory.failWithMessageAndStatus(NOT_FOUND,
            "staffService.update.notFound", [sId])
    }
    protected Result<Staff> updateStaffInfo(Staff s1, Map body, String timezone) {
        s1.with {
            if (body.name) name = body.name
            if (body.username) username = body.username
            if (body.password) password = body.password
            if (body.email) email = body.email
            if (body.manualSchedule) manualSchedule = body.manualSchedule
            if (body.isAvailable) isAvailable = body.isAvailable
            if (body.personalPhoneNumber) {
                personalPhoneAsString = body.personalPhoneNumber
            }
        }
        if (s1.phone && body.awayMessage) {
            s1.phone.awayMessage = body.awayMessage
            if (!s1.phone.save()) {
                return resultFactory.failWithValidationErrors(s1.phone.errors)
            }
        }
        if (body.schedule instanceof Map && s1.schedule.instanceOf(WeeklySchedule)) {
            Result res = (s1.schedule as WeeklySchedule)
                .updateWithIntervalStrings(body.schedule as Map, timezone)
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
        if (s1.validate()) {
            resultFactory.success(s1)
        }
        else {
            resultFactory.failWithValidationErrors(s1.errors)
        }
    }
    protected Result<Staff> updatePhoneNumber(Staff s1, Map body) {
        if (body.phone || body.phoneId) {
            Phone p1 = s1.phone ?: new Phone([:])
            p1.updateOwner(s1)
            Result<Phone> res
            if (body.phone) {
                PhoneNumber pNum = new PhoneNumber(number:body.phone as String)
                res = phoneService.updatePhoneForNumber(p1, pNum)
            }
            else {
                res = phoneService.updatePhoneForApiId(p1, body.phoneId as String)
            }
            res.then({
                if (p1.save()) {
                    resultFactory.success(s1)
                }
                else { resultFactory.failWithValidationErrors(p1.errors) }
            }) as Result<Staff>
        }
        else { resultFactory.success(s1) }
    }
}
