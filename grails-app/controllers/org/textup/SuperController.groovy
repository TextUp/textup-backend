package org.textup

import grails.compiler.GrailsTypeChecked
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.encoding.PasswordEncoder
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@GrailsTypeChecked
@Secured(Roles.ADMIN)
@Transactional
class SuperController {

    MailService mailService

    // Page handlers
    // -------------

    void index() {
        flash.previousPage = "index"
        [unverifiedOrgs: Organization.findAllByStatus(OrgStatus.PENDING)]
    }

    void approved() {
        flash.previousPage = "approved"
        [orgs: Organization.findAllByStatus(OrgStatus.APPROVED)]
    }

    void rejected() {
        flash.previousPage = "rejected"
        [orgs: Organization.findAllByStatus(OrgStatus.REJECTED)]
    }

    void settings() {
        [staff: IOCUtils.security.currentUser]
    }

    void updateSettings() {
        TypeMap qParams = TypeMap.create(params)
        String newPassword = qParams.newPassword
        if (newPassword && newPassword != qParams.confirmNewPassword) {
            flash.messages = ["New passwords must match."]
            redirect(action: "settings")
            return
        }

        Staff s1 = IOCUtils.security.currentUser as Staff
        String oldUsername = s1.username

        // if wanting to change password, need to validate current password first
        if (newPassword) {
            if (qParams.currentPassword &&
                AuthUtils.isSecureStringValid(s1.password, qParams.string("currentPassword"))) {
                s1.password = newPassword
            }
            else {
                flash.messages = ["Could not update password. Current password is either blank or incorrect."]
                redirect(action: "settings")
                return
            }
        }
        // update other properties
        s1.properties.each { obj, val ->
            String prop = obj as String
            if (params[prop] && s1.hasProperty(prop)) {
                s1.setProperty(prop, params[prop])
            }
        }
        // save new settings
        if (s1.save()) {
            IOCUtils.security.reauthenticate(oldUsername)
            flash.messages = ["Successfully updated settings."]
        }
        else {
            flash.errorObj = s1
            s1.discard()
        }
        redirect(action: "settings")
    }

    // Actions
    // -------

    void logout() { // '/j_spring_security_logout'
        redirect uri: (SpringSecurityUtils.securityConfig.logout as Map).filterProcessesUrl
    }

    void rejectOrg() {
        Organizations.mustFindForId(params.long("id"))
            .ifFail { Result<?> failRes -> flash.messages = failRes.errorMessages }
            .thenEnd { Organization org1 ->
                Staff admin = Staffs.buildForOrgIdAndOptions(org1.id, null, [StaffStatus.ADMIN])
                    .list(max: 1)[0]
                if (admin) {
                    org1.status = OrgStatus.REJECTED
                    DomainUtils.trySave(org1)
                        .ifFail {
                            flash.errorObj = org1
                            org1.discard()
                        }
                        .then {
                            flash.messages = ["Successfully rejected ${org1.name}"]
                            mailService.notifyRejection(admin)
                        }
                        .ifFailEnd("rejectOrg") { Result<?> failRes ->
                            flash.messages = failRes.errorMessages
                        }
                }
                else { flash.messages = ["Could not find admins for ${org1.name}."] }
            }
        flash.previousPage ? redirect(action: flash.previousPage) : redirect(action: "index")
    }

    void approveOrg() {
        Organizations.mustFindForId(params.long("id"))
            .ifFail { Result<?> failRes -> flash.messages = failRes.errorMessages }
            .thenEnd { Organization org1 ->
                Staff admin = Staffs.buildForOrgIdAndOptions(org1.id, null, [StaffStatus.ADMIN])
                    .list(max: 1)[0]
                if (admin) {
                    org1.status = OrgStatus.APPROVED
                    DomainUtils.trySave(org1)
                        .ifFail {
                            flash.errorObj = org1
                            org1.discard()
                        }
                        .then {
                            flash.messages = ["Successfully approved ${org1.name}"]
                            mailService.notifyApproval(admin)
                        }
                        .ifFailEnd("approveOrg") { Result<?> failRes ->
                            flash.messages = failRes.errorMessages
                        }
                }
                else { flash.messages = ["Could not find admins for ${org1.name}."] }
            }
        flash.previousPage ? redirect(action: flash.previousPage) : redirect(action: "index")
    }
}
