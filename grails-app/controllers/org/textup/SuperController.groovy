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
@Secured("ROLE_ADMIN")
@Transactional
class SuperController {

    MailService mailService

    // Page handlers
    // -------------

    def index() {
        flash.previousPage = "index"
        [unverifiedOrgs: Organization.findAllByStatus(OrgStatus.PENDING)]
    }

    def approved() {
        flash.previousPage = "approved"
        [orgs: Organization.findAllByStatus(OrgStatus.APPROVED)]
    }

    def rejected() {
        flash.previousPage = "rejected"
        [orgs: Organization.findAllByStatus(OrgStatus.REJECTED)]
    }

    def settings() {
        [staff: IOCUtils.security.currentUser]
    }

    def updateSettings() {
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
        DomainUtils.trySave(s1)
            .ifFailAndPreserveError { Result<?> failRes ->
                flash.errorObj = s1
                s1.discard()
            }
            .thenEnd {
                IOCUtils.security.reauthenticate(oldUsername)
                flash.messages = ["Successfully updated settings."]
            }
        redirect(action: "settings")
    }

    // Actions
    // -------

    def logout() { // '/j_spring_security_logout'
        redirect uri: (SpringSecurityUtils.securityConfig.logout as Map).filterProcessesUrl
    }

    def rejectOrg() {
        Organizations.mustFindForId(params.long("id"))
            .ifFailAndPreserveError { Result<?> failRes -> flash.messages = failRes.errorMessages }
            .thenEnd { Organization org1 ->
                Staff admin = Staffs.buildForOrgIdAndOptions(org1.id, null, [StaffStatus.ADMIN])
                    .list(max: 1)[0]
                if (admin) {
                    org1.status = OrgStatus.REJECTED
                    DomainUtils.trySave(org1)
                        .then {
                            flash.messages = ["Successfully rejected ${org1.name}"]
                            mailService.notifyRejection(admin)
                        }
                        .ifFailAndPreserveError("rejectOrg") { Result<?> failRes ->
                            flash.messages = failRes.errorMessages
                            org1.discard()
                        }
                }
                else { flash.messages = ["Could not find admins for ${org1.name}."] }
            }
        flash.previousPage ? redirect(action: flash.previousPage) : redirect(action: "index")
    }

    def approveOrg() {
        Organizations.mustFindForId(params.long("id"))
            .ifFailAndPreserveError { Result<?> failRes -> flash.messages = failRes.errorMessages }
            .thenEnd { Organization org1 ->
                Staff admin = Staffs.buildForOrgIdAndOptions(org1.id, null, [StaffStatus.ADMIN])
                    .list(max: 1)[0]
                if (admin) {
                    org1.status = OrgStatus.APPROVED
                    DomainUtils.trySave(org1)
                        .then {
                            flash.messages = ["Successfully approved ${org1.name}"]
                            mailService.notifyApproval(admin)
                        }
                        .ifFailAndPreserveError("approveOrg") { Result<?> failRes ->
                            flash.messages = failRes.errorMessages
                            org1.discard()
                        }
                }
                else { flash.messages = ["Could not find admins for ${org1.name}."] }
            }
        flash.previousPage ? redirect(action: flash.previousPage) : redirect(action: "index")
    }
}
