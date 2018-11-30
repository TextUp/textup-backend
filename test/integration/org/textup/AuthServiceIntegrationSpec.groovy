package org.textup

import org.textup.test.*

class AuthServiceIntegrationSpec extends CustomSpec {

	def authService

    def setup() {
    	setupIntegrationData()
    }
    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test validating"() {
        given: "a staff with lock code"
        String code = "3920"
        String pwd = "mypassword"
        Staff staff1 = new Staff(username:"1val${iterNum}",password:pwd,
            name:"Staff${iterNum}", email:"staff${iterNum}@textup.org",
            org:org, personalPhoneAsString:"1112223333", lockCode:code)
        staff1.save(flush:true, failOnError:true)

        expect:
        authService.isValidLockCode(staff1.username, "blahblah") == false
        authService.isValidLockCode(staff1.username, code) == true
        authService.isValidUsernamePassword(staff1.username, "blahblah") == false
        authService.isValidUsernamePassword(staff1.username, pwd) == true
    }
}
