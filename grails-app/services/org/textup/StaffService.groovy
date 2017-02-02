package org.textup

import com.twilio.sdk.resource.instance.IncomingPhoneNumber
import com.twilio.sdk.TwilioRestClient
import com.twilio.sdk.TwilioRestException
import grails.compiler.GrailsTypeChecked
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
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

    Result<Staff> create(Map body, String timezone) {
        verifyCreateRequest(body).then({ ->
            Result.<Staff>waterfall(
                this.&fillStaffInfo.curry(new Staff(), body, timezone),
                this.&completeStaffCreation.rcurry(body)
            )
        }) as Result<Staff>
    }
    protected Result verifyCreateRequest(Map body) {
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
            resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                "staffService.create.couldNotVerifyCaptcha")
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
                return resultFactory.failWithMessageAndStatus(UNPROCESSABLE_ENTITY,
                    "staffService.lockCodeFormat")
            }
        }
        if (body.personalPhoneNumber != null) {
            s1.personalPhoneNumber = new PhoneNumber(number:
                body.personalPhoneNumber as String)
        }
        if (body.schedule instanceof Map && s1.schedule.instanceOf(WeeklySchedule)) {
            Result res = WeeklySchedule.get(s1.schedule.id)
                ?.updateWithIntervalStrings(body.schedule as Map, timezone)
            if (!res.success) { return res }
        }
        // leave validation for later step because might not have org
        resultFactory.success(s1)
    }
    protected Result<Staff> completeStaffCreation(Staff s1, Map body) {
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
        }) as Result<Staff>
    }
    protected Result<Organization> tryAddStaffToOrg(Staff s1, def rawInfo) {
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
            }
            else if (s1.status == StaffStatus.STAFF) {
                String pwd = body.password as String,
                    lockCode = body.lockCode ? body.lockCode as String :
                        Constants.DEFAULT_LOCK_CODE
                res = mailService.notifyStaffOfSignup(s1, pwd, lockCode)
            }
        }
        (res && !res.success) ? res : resultFactory.success(s1)
    }

    // Update
    // ------

    Result<Staff> update(Long staffId, Map body, String timezone) {
        Result.<Staff>waterfall(
            this.&findStaffForId.curry(staffId),
            this.&fillStaffInfo.rcurry(body, timezone),
            this.&tryUpdateStatus.rcurry(body),
            phoneService.&createOrUpdatePhone.rcurry(body)
        ).then({ Staff s1 ->
            StaffStatus oldStatus = s1.getPersistentValue("status") as StaffStatus
            // email notifications if changing away from pending
            if (oldStatus.isPending && !s1.status.isPending) {
                Result res = s1.status.isActive ?
                    mailService.notifyPendingOfApproval(s1) :
                    mailService.notifyPendingOfRejection(s1)
                if (!res.success) { return res }
            }
            resultFactory.success(s1)
        }) as Result
    }
    protected Result<Staff> findStaffForId(Long sId) {
        Staff s1 = Staff.get(sId)
        s1 ? resultFactory.success(s1) : resultFactory.failWithMessageAndStatus(NOT_FOUND,
            "staffService.update.notFound", [sId])
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
