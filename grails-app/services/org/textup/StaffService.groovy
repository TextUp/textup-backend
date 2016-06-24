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
            // only allowed to change status if is admin and organization
            // is not pending
            if (body.status && o1.id && authService.isAdminAt(o1.id) &&
                o1.status != OrgStatus.PENDING) {
                s1.status = Helpers.convertEnum(StaffStatus, body.status)
            }
            // initially validate first so we know that staff is valid
            // before trying to send out email notification and adding a phone
            if (s1.save()) {
                Result.<Staff>waterfall(
                    phoneService.&createOrUpdatePhone.curry(s1, body),
                    this.&notifyAfterCreation.rcurry(o1, body)
                ).then({ Staff sameS1 ->
                    if (sameS1.save()) {
                        resultFactory.success(sameS1)
                    }
                    else { resultFactory.failWithValidationErrors(sameS1.errors) }
                }) as Result
            }
            else {
                resultFactory.failWithValidationErrors(s1.errors)
            }
        }) as Result
    }

    Result<Staff> addRoleToStaff(Long sId) {
        Staff s1 = Staff.get(sId)
        if (s1) {
            Role role = Role.findOrCreateByAuthority("ROLE_USER")
            if (!role.save()) {
                return resultFactory.failWithValidationErrors(role.errors)
            }
            try {
                StaffRole.create(s1, role, true)
                resultFactory.success(s1)
            }
            catch (e) {
                log.error("StaffService.addRoleToStaff: ${e.message}, $e")
                resultFactory.failWithThrowable(e)
            }
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "staffService.update.notFound", [sId])
        }
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
        }
        s1.org = org

        resultFactory.success(org)
    }

    protected Result<Staff> notifyAfterCreation(Staff s1, Organization o1, Map body) {
        Result res
        if (o1.status == OrgStatus.PENDING) {
            res = mailService.notifySuperOfNewOrganization(o1.name)
        }
        else {
            if (s1.status == StaffStatus.PENDING) {
                res = mailService.notifyAdminsOfPendingStaff(s1.name, o1.getAdmins())
            } else if (s1.status == StaffStatus.STAFF) {
                res = mailService.notifyStaffOfSignup(s1, body.password as String)
            }
        }
        (res && !res.success) ? res : resultFactory.success(s1)
    }

    // Update
    // ------

    Result<Staff> update(Long staffId, Map body, String timezone) {
        Result.<Staff>waterfall(
            this.&findStaffForId.curry(staffId),
            this.&updateStaffInfo.rcurry(body, timezone),
            phoneService.&createOrUpdatePhone.rcurry(body)
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
        }
        if (body.personalPhoneNumber) {
            s1.personalPhoneNumber = new PhoneNumber(number:
                body.personalPhoneNumber as String)
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
}
