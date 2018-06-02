package org.textup

import grails.compiler.GrailsTypeChecked
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.transaction.TransactionStatus
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.validator.PhoneNumber

import org.springframework.context.MessageSource

@GrailsTypeChecked
@Transactional
class StaffService {

    ResultFactory resultFactory
    AuthService authService
    MailService mailService
    PhoneService phoneService
    GrailsApplication grailsApplication

    // Create
    // ------

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
            catch (Throwable e) {
                log.error("StaffService.addRoleToStaff: ${e.message}, $e")
                e.printStackTrace()
                resultFactory.failWithThrowable(e)
            }
        }
        else {
            resultFactory.failWithCodeAndStatus("staffService.update.notFound",
                ResultStatus.NOT_FOUND, [sId])
        }
    }

    Result<Staff> create(Map body, String timezone) {
        verifyCreateRequest(body)
            .then({ fillStaffInfo(new Staff(), body, timezone) })
            .then({ Staff s1 -> completeStaffCreation(s1, body, timezone) })
            .then({ Staff s1 -> resultFactory.success(s1, ResultStatus.CREATED) })
    }
    protected Result<Void> verifyCreateRequest(Map body) {
        // don't need captcha to verify that user is not a bot if
        // we are trying to create a staff member after logging in
        if (authService.isActive) {
            return resultFactory.success()
        }
        Map response = [:]
        String captcha = body?.captcha
        if (captcha) {
            String verifyLink = grailsApplication
                .flatConfig["textup.apiKeys.reCaptcha.verifyEndpoint"]
            String secret = grailsApplication
                .flatConfig["textup.apiKeys.reCaptcha.secret"]
            response = doVerifyRequest("${verifyLink}?secret=${secret}&response=${captcha}")
        }
        if (response.success) {
            resultFactory.success()
        }
        else {
            resultFactory.failWithCodeAndStatus("staffService.create.couldNotVerifyCaptcha",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
    }
    // for mocking during testing
    private Map doVerifyRequest(String requestUrl) {
        (new RestBuilder())
            .post(requestUrl)
            .json as Map ?: [:]
    }
    protected Result<Staff> fillStaffInfo(Staff s1, Map body, String timezone) {
        s1.with {
            if (body.name) name = body.name
            if (body.username) username = body.username
            if (body.password) password = body.password
            if (body.email) email = body.email
            if (body.manualSchedule != null) manualSchedule = body.manualSchedule
            if (body.isAvailable != null) isAvailable = body.isAvailable
        }
        if (body.lockCode) {
            if (body.lockCode ==~ /\d{${Constants.LOCK_CODE_LENGTH}}/) {
                s1.lockCode = body.lockCode
            }
            else {
                return resultFactory.failWithCodeAndStatus("staffService.lockCodeFormat",
                    ResultStatus.UNPROCESSABLE_ENTITY)
            }
        }
        if (body.personalPhoneNumber != null) {
            s1.personalPhoneNumber = new PhoneNumber(number:body.personalPhoneNumber as String)
        }
        if (body.schedule instanceof Map && s1.schedule.instanceOf(WeeklySchedule)) {
            WeeklySchedule wSched = WeeklySchedule.get(s1.schedule.id)
            if (!wSched) {
                return resultFactory.failWithCodeAndStatus("staffService.fillStaffInfo.scheduleNotFound",
                    ResultStatus.UNPROCESSABLE_ENTITY, [s1.schedule.id, s1.id])
            }
            Result<Schedule> res = wSched.updateWithIntervalStrings(body.schedule as Map, timezone)
            if (!res.success) {
                return resultFactory.failWithResultsAndStatus([res], res.status)
            }
        }
        // leave validation for later step because might not have org
        resultFactory.success(s1)
    }
    protected Result<Staff> completeStaffCreation(Staff s1, Map body, String timezone) {
        tryAddStaffToOrg(s1, body.org).then({ Organization o1 ->
            // only allowed to change status if is admin and organization
            // is not pending
            if (body.status && o1.id && authService.isAdminAt(o1.id) &&
                o1.status != OrgStatus.PENDING) {
                s1.status = Helpers.convertEnum(StaffStatus, body.status)
            }
            // initially save first so we know that staff is valid
            // before trying to send out email notification and adding a phone
            if (s1.save()) {
                phoneService
                    .mergePhone(s1, body, timezone)
                    .then({ notifyAfterCreation(s1, o1, body) })
            }
            else { resultFactory.failWithValidationErrors(s1.errors) }
        })
    }
    protected Result<Organization> tryAddStaffToOrg(Staff s1, def rawInfo) {
        if (!rawInfo || !(rawInfo instanceof Map)) {
            return resultFactory.failWithCodeAndStatus("staffService.create.mustSpecifyOrg",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        Map orgInfo = rawInfo as Map
        Organization org
        //if we specify id then we must be associating with existing
        if (orgInfo.id) { // existing organization
            org = Organization.get(Helpers.to(Long, orgInfo.id))
            if (!org) {
                return resultFactory.failWithCodeAndStatus("staffService.create.orgNotFound",
                    ResultStatus.NOT_FOUND, [orgInfo.id])
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
        Result<?> res
        if (o1.status == OrgStatus.PENDING) {
            res = mailService.notifyAboutPendingOrg(o1)
        }
        else if (s1.status == StaffStatus.PENDING) {
            res = mailService.notifyAboutPendingStaff(s1, o1.getAdmins())
        }
        else if (s1.status == StaffStatus.STAFF) {
            String pwd = body.password as String,
                lockCode = body.lockCode ? body.lockCode as String :
                    Constants.DEFAULT_LOCK_CODE
            Staff invitedBy = authService.loggedIn
            res = mailService.notifyInvitation(invitedBy, s1, pwd, lockCode)
        }

        if (res?.success) {
            resultFactory.success(s1)
        }
        else { resultFactory.failWithResultsAndStatus([res], res.status) }
    }

    // Update
    // ------

    Result<Staff> update(Long staffId, Map body, String timezone) {
        findStaffForId(staffId)
            .then({ Staff s1 -> fillStaffInfo(s1, body, timezone) })
            .then({ Staff s1 -> tryUpdateStatus(s1, body) })
            .then({ Staff s1 -> phoneService.mergePhone(s1, body, timezone) })
            .then({ Staff s1 ->
                StaffStatus oldStatus = s1.getPersistentValue("status") as StaffStatus
                // email notifications if changing away from pending
                if (oldStatus.isPending && !s1.status.isPending) {
                    Result<?> res = s1.status.isActive ?
                        mailService.notifyApproval(s1) :
                        mailService.notifyRejection(s1)
                    if (!res.success) {
                        return resultFactory.failWithResultsAndStatus([res], res.status)
                    }
                }
                resultFactory.success(s1)
            })
    }
    protected Result<Staff> findStaffForId(Long sId) {
        Staff s1 = Staff.get(sId)
        if (s1) {
            resultFactory.success(s1)
        }
        else {
            resultFactory.failWithCodeAndStatus("staffService.update.notFound",
                ResultStatus.NOT_FOUND, [sId])
        }
    }
    protected Result<Staff> tryUpdateStatus(Staff s1, Map body) {
        //can only update status if you are an admin
        if (body.status && authService.isAdminAtSameOrgAs(s1.id)) {
            s1.status = Helpers.convertEnum(StaffStatus, body.status)
        }
        if (s1.save()) {
            resultFactory.success(s1)
        }
        else { resultFactory.failWithValidationErrors(s1.errors) }
    }
}
