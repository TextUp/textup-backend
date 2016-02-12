package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*
import grails.compiler.GrailsCompileStatic
import org.codehaus.groovy.grails.commons.GrailsApplication

@GrailsCompileStatic
@Transactional
class PasswordResetService {

	GrailsApplication grailsApplication
	ResultFactory resultFactory
	MailService mailService

    // Request
    // -------

	Result requestReset(String username) {
        Staff s1 = Staff.findByUsername(username?.trim()?.toLowerCase())
        if (!s1) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "passwordResetService.requestReset.staffNotFound", [username])
        }
        else if (!s1.email) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "passwordResetService.requestReset.staffNoEmail")
        }
        generateResetToken(s1).then({ PasswordResetToken pr ->
            mailService.notifyPasswordReset(s1, pr.token)
        }) as Result
    }
    protected Result<PasswordResetToken> generateResetToken(Staff s1) {
        Integer resetTokenSize = Helpers.toInteger(grailsApplication.flatConfig["textup.resetTokenSize"])
        String tokenString = Helpers.randomAlphanumericString(resetTokenSize)
        //ensure that our generated token is unique
        while (PasswordResetToken.findByToken(tokenString) != null) {
            tokenString = Helpers.randomAlphanumericString(resetTokenSize)
        }
        PasswordResetToken resetToken =
            new PasswordResetToken(toBeResetId:s1.id, token:tokenString)
        if (resetToken.save()) {
            resultFactory.success(resetToken)
        }
        else { resultFactory.failWithValidationErrors(resetToken.errors) }
    }

    // Complete
    // --------

    Result<Staff> resetPassword(String token, String password) {
    	PasswordResetToken resetToken = PasswordResetToken.findByToken(token)
        if (!resetToken) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "passwordResetService.resetPassword.tokenNotFound", [token])
        }
        else if (resetToken.isExpired) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "passwordResetService.resetPassword.tokenExpired")
        }
        Staff s1 = Staff.get(resetToken.toBeResetId)
        if (!s1) {
            log.error("PasswordResetService.resetPassword: for token '$token' \
                with toBeResetId '${resetToken.toBeResetId}', \
                could not find a staff with that id")
            return resultFactory.failWithMessageAndStatus(INTERNAL_SERVER_ERROR,
                "passwordResetService.resetPassword.couldNotComplete")
        }
        s1.password = password
        if (s1.save()) {
            resetToken.expireNow()
            if (resetToken.save()) {
                resultFactory.success(s1)
            }
            else { resultFactory.failWithValidationErrors(resetToken.errors) }
        }
        else { resultFactory.failWithValidationErrors(s1.errors) }
    }
}
