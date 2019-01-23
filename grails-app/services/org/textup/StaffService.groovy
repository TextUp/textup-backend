package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class StaffService implements ManagesDomain.Updater<Staff> {

    MailService mailService
    OrganizationService organizationService
    PhoneService phoneService

    // [NOTE] `create` can be called by anybody
    @RollbackOnResultFailure
    Result<Staff> create(TypeMap body) {
        String timezone = body.string("timezone")
        organizationService.tryFindOrCreate(body.typeMapNoNull("org"))
            .then { Organization org1 -> Roles.tryGetUserRole().curry(org1) }
            .then { Organization org1, Role r1 ->
                Staff.tryCreate(r1, org1, body.string("name"), body.string("username"),
                    body.string("password"), body.string("email"))
            }
            .then { Staff s1 -> trySetFields(s1, body, timezone) }
            .then { Staff s1 -> trySetLockCode(s1, body.string("lockCode")) }
            .then { Staff s1 -> trySetStatus(s1, body.enum(StaffStatus, "status")) }
            .then { Staff s1 ->
                tryUpdatePhone(s1, body.typeMapNoNull("phone"), timezone).curry(s1)
            }
            .then { Staff s1 -> finishCreate(s1, body) }
    }

    @RollbackOnResultFailure
    Result<Staff> update(Long staffId, TypeMap body) {
        String timezone = body.string("timezone")
        Staffs.mustFindForId(staffId)
            .then { Staff s1 -> trySetFields(s1, body, timezone) }
            .then { Staff s1 -> trySetLockCode(s1, body.string("lockCode")) }
            .then { Staff s1 -> trySetStatus(s1, body.enum(StaffStatus, "status")) }
            .then { Staff s1 ->
                tryUpdatePhone(s1, body.typeMapNoNull("phone"), timezone).curry(s1)
            }
            .then { Staff s1 -> finishUpdate(s1, body) }
    }

    // Helpers
    // -------

    protected Result<Staff> finishCreate(Staff s1, TypeMap body) {
        Result<?> res = Result.void()
        if (s1.org.status == OrgStatus.PENDING) {
            res = mailService.notifyAboutPendingOrg(s1.org)
        }
        else if (s1.status == StaffStatus.PENDING) {
            List<Staff> admins = Staffs
                .buildForOrgIdAndOptions(s1.org.id, null, [StaffStatus.ADMIN])
                .list()
            res = mailService.notifyAboutPendingStaff(s1, admins)
        }
        else if (s1.status == StaffStatus.STAFF) {
            res = AuthUtils.tryGetAuthUser()
                .then { Staff authUser ->
                    mailService.notifyInvitation(authUser,
                        s1,
                        body.string("password"),
                        body.string("lockCode", Constants.DEFAULT_LOCK_CODE))
                }
        }
        res.then { IOCUtils.resultFactory.success(s1, ResultStatus.CREATED) }
    }

    protected Result<Staff> finishUpdate(Staff s1, TypeMap body) {
        Result<?> res = Result.void()
        // email notifications if changing away from pending
        StaffStatus oldStatus = s1.getPersistentValue("status") as StaffStatus
        if (oldStatus.isPending() && !s1.status.isPending()) {
            res = s1.status.isActive() ?
                mailService.notifyApproval(s1) :
                mailService.notifyRejection(s1)
        }
        res.then { IOCUtils.resultFactory.success(s1) }
    }

    protected Result<Staff> trySetFields(Staff s1, TypeMap body) {
        s1.with {
            if (body.name) name = body.name
            if (body.username) username = body.username
            if (body.password) password = body.password
            if (body.email) email = body.email
            if (body.personalNumber != null) {
                personalNumber = PhoneNumber.create(body.string("personalNumber"))
            }
        }
        DomainUtils.trySave(s1)
    }

    protected Result<Staff> trySetLockCode(Staff s1, String lockCode) {
        if (lockCode) {
            // Need to validate here because, once saved, the lock code is obfuscated
            if (!ValidationUtils.isValidLockCode(lockCode)) {
                return IOCUtils.resultFactory.failWithCodeAndStatus("staffService.lockCodeFormat",
                    ResultStatus.UNPROCESSABLE_ENTITY)
            }
            s1.lockCode = lockCode
        }
        DomainUtils.trySave(s1)
    }

    protected Result<Staff> trySetStatus(Staff s1, StaffStatus newStatus) {
        // Only want to do admin check if the user is attempting to update this
        if (!newStatus) {
            return IOCUtils.resultFactory.success(s1)
        }
        AuthUtils.tryGetAuthId()
            .then { Long authId -> Organizations.tryIfAdmin(s1.org.id, authId) }
            .then {
                int numAdmins = Staffs
                    .buildForOrgIdAndOptions(s1.org.id, null, [StaffStatus.ADMIN])
                    .count()
                if (numAdmins == 1 && s1.status == StaffStatus.ADMIN &&
                    newStatus != StaffStatus.ADMIN) {
                    IOCUtils.resultFactory.failWithCodeAndStatus(
                        "staffService.trySetStatus.lastAdmin") // TODO
                }
                else {
                    s1.status = newStatus
                    DomainUtils.trySave(s1)
                }
            }
    }

    // [NOTE] ensure that a public user cannot provision creation of phone, must be active admin
    protected Result<?> tryUpdatePhone(Staff s1, TypeMap phoneInfo, String timezone) {
        // Only want to do admin check if the user is attempting to update this
        if (!phoneInfo) {
            return Result.void()
        }
        AuthUtils.tryGetAuthId()
            .then { Long authId -> Organizations.tryIfAdmin(s1.org.id, authId) }
            .then { Phones.mustFindActiveForOwner(s1.id, PhoneOwnershipType.INDIVIDUAL, true) }
            .then { Phone p1 -> phoneService.update(p1, phoneInfo, timezone) }
    }
}
