package org.textup

import grails.plugin.springsecurity.SpringSecurityUtils
import org.springframework.security.access.annotation.Secured
import grails.transaction.Transactional

@Secured("ROLE_ADMIN")
@Transactional
class SuperController {

    def springSecurityService

    def index() {
        [unverifiedOrgs:Organization.findAllByStatus(Constants.ORG_PENDING)]
    }

    def settings() {
        [staff:springSecurityService.currentUser]
    }

    def updateSettings() {
        if (params.password && params.password != params.confirmPassword) {
            flash.message = "New passwords must match."
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
                flash.message = "Successfully updated settings."
            }
            else {
                flash.errorObj = s1
                s1.discard()
            }
        }
        redirect(action: "settings")
    }

    def logout() {
        // '/j_spring_security_logout'
        redirect uri: SpringSecurityUtils.securityConfig.logout.filterProcessesUrl
    }

    def rejectOrg() {

    }

    def approveOrg() {

    }
}
