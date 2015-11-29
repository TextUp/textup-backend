package org.textup

import grails.plugin.springsecurity.SpringSecurityUtils
import org.springframework.security.access.annotation.Secured
import grails.transaction.Transactional

@Secured("ROLE_ADMIN")
@Transactional
class SuperController {

    def springSecurityService
    def mailService
    def resultFactory

    ///////////////////
    // Page handlers //
    ///////////////////

    def index() {
        flash.previousPage = "index"
        [unverifiedOrgs:Organization.findAllByStatus(Constants.ORG_PENDING)]
    }

    def approved() {
        flash.previousPage = "approved"
        [orgs:Organization.findAllByStatus(Constants.ORG_APPROVED)]
    }

    def rejected() {
        flash.previousPage = "rejected"
        [orgs:Organization.findAllByStatus(Constants.ORG_REJECTED)]
    }

    def settings() {
        [staff:springSecurityService.currentUser]
    }

    def updateSettings() {
        if (params.password && params.password != params.confirmPassword) {
            flash.messages = ["New passwords must match."]
        }
        else {
            Staff s1 = springSecurityService.currentUser
            String oldUsername = s1.username
            s1.properties.each { String prop, val ->
                if (params."${prop}") {
                    s1."${prop}" = params."${prop}"
                }
            }
            if (s1.save()) {
                springSecurityService.reauthenticate(oldUsername)
                flash.messages = ["Successfully updated settings."]
            }
            else {
                flash.errorObj = s1
                s1.discard()
            }
        }
        redirect(action: "settings")
    }

    /////////////
    // Actions //
    /////////////

    def logout() {
        // '/j_spring_security_logout'
        redirect uri: SpringSecurityUtils.securityConfig.logout.filterProcessesUrl
    }

    def rejectOrg() {
        Org org = Organization.get(id)
        if (org && org.admins[0]) {
            org.status = Constants.ORG_REJECTED
            if (org.save()) {
                flash.messages = ["Successfully rejected ${org.name}"]
                Result res = mailService.notifyNewOrganizationOfRejection(org.admins[0])
                if (!res.success) {
                    log.error("SuperController.rejectOrg: could not notify $org of rejection: ${res.payload}")
                    flash.messages = resultFactory.extractMessages(res)
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
        Org org = Organization.get(id)
        if (org && org.admins[0]) {
            org.status = Constants.ORG_APPROVED
            if (org.save()) {
                flash.messages = ["Successfully rejected ${org.name}"]
                Result res = mailService.notifyNewOrganizationOfApproval(org.admins[0])
                if (!res.success) { 
                    log.error("SuperController.approveOrg: could not notify $org of approval: ${res.payload}")
                    flash.messages = resultFactory.extractMessages(res)
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
