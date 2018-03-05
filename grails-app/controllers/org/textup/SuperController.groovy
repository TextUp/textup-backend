package org.textup

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.transaction.Transactional
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.encoding.PasswordEncoder
import org.textup.type.OrgStatus

@GrailsCompileStatic
@Secured("ROLE_ADMIN")
@Transactional
class SuperController {

    MailService mailService
    PasswordEncoder passwordEncoder
    SpringSecurityService springSecurityService

    // Page handlers
    // -------------

    def index() {
        flash.previousPage = "index"
        [unverifiedOrgs:Organization.findAllByStatus(OrgStatus.PENDING)]
    }

    def approved() {
        flash.previousPage = "approved"
        [orgs:Organization.findAllByStatus(OrgStatus.APPROVED)]
    }

    def rejected() {
        flash.previousPage = "rejected"
        [orgs:Organization.findAllByStatus(OrgStatus.REJECTED)]
    }

    def settings() {
        [staff:springSecurityService.currentUser]
    }

    def updateSettings() {
        String newPassword = params.newPassword
        if (newPassword && newPassword != params.confirmNewPassword) {
            flash.messages = ["New passwords must match."]
            return redirect(action: "settings")
        }

        Staff s1 = springSecurityService.currentUser as Staff
        String oldUsername = s1.username

        // if wanting to change password, need to validate current password first
        if (newPassword) {
            if (params.currentPassword &&
                passwordEncoder.isPasswordValid(s1.password, params.currentPassword as String, null)) {

                s1.password = newPassword
            }
            else {
                flash.messages = ["Could not update password. Current password is either blank or incorrect."]
                return redirect(action: "settings")
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
            springSecurityService.reauthenticate(oldUsername)
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

    def logout() {
        // '/j_spring_security_logout'
        redirect uri: (SpringSecurityUtils.securityConfig.logout as Map).filterProcessesUrl
    }

    def rejectOrg() {
        Organization org = Organization.get(params.long("id"))
        if (org && org.getAdmins()[0]) {
            org.status = OrgStatus.REJECTED
            if (org.save()) {
                flash.messages = ["Successfully rejected ${org.name}"]
                Result res = mailService.notifyRejection(org.getAdmins()[0])
                if (!res.success) {
                    log.error("SuperController.rejectOrg: could not notify \
                        $org of rejection: ${res.payload}")
                    flash.messages = res.errorMessages
                }
            }
            else {
                flash.errorObj = org
                org.discard()
            }
        }
        else {
            flash.messages = ["Could not find organization or admin."]
        }
        flash.previousPage ? redirect(action:flash.previousPage) : redirect(action:"index")
    }

    def approveOrg() {
        Organization org = Organization.get(params.long("id"))
        if (org && org.getAdmins()[0]) {
            org.status = OrgStatus.APPROVED
            if (org.save()) {
                flash.messages = ["Successfully approved ${org.name}"]
                Result res = mailService.notifyApproval(org.getAdmins()[0])
                if (!res.success) {
                    log.error("SuperController.approveOrg: could not notify \
                        $org of approval: ${res.payload}")
                    flash.messages = res.errorMessages
                }
            }
            else {
                flash.errorObj = org
                org.discard()
            }
        }
        else {
            flash.messages = ["Could not find organization or admin."]
        }
        flash.previousPage ? redirect(action:flash.previousPage) : redirect(action:"index")
    }
}
