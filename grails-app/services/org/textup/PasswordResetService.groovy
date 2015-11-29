package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*

@Transactional
class PasswordResetService {

	def grailsLinkGenerator
	def grailsApplication
	def resultFactory
	def mailService

	Result requestReset(String username) {
        Staff s1 = Staff.findByUsername(username)
        if (s1) {
            String toEmail = s1.email 
            if (toEmail) {
                Result<PasswordResetToken> tokenRes = generateResetToken(s1)
                if (tokenRes.success) {
                	String resetLink = grailsLinkGenerator.link(namespace:"v1", controller:"passwordReset", action:"resetPassword", absolute:true, params:[token:tokenRes.payload.token])
                	mailService.notifyPasswordReset(s1, resetLink)
                }
                else { tokenRes }
            }
            else {
                resultFactory.failWithMessageAndStatus(NOT_FOUND, "passwordResetService.requestReset.staffNoEmail")
            }
        }
        else { 
        	resultFactory.failWithMessageAndStatus(NOT_FOUND, "passwordResetService.requestReset.staffNotFound", [username])
        }
    }

    Result<Staff> resetPassword(String token, String password) {
    	PasswordResetToken resetToken = PasswordResetToken.findByToken(token)
        if (resetToken) {
            if (!resetToken.isExpired) {
                Staff s1 = Staff.get(resetToken.toBeResetId)
                if (s1) {
                    s1.password = password 
                    if (s1.save()) {
                        resetToken.expireNow()
                        if (resetToken.save()) { resultFactory.success(s1) }
                        else { resultFactory.failWithValidationErrors(resetToken.errors) }
                    }
                    else { resultFactory.failWithValidationErrors(s1.errors) }
                }
                else {
                    log.error("PasswordResetService.resetPassword: for token '$token' with toBeResetId '${resetToken.toBeResetId}', could not find a staff with that id")
                    resultFactory.failWithMessageAndStatus(INTERNAL_SERVER_ERROR, "passwordResetService.resetPassword.couldNotComplete")
                }
            }
            else {
            	resultFactory(BAD_REQUEST, "passwordResetService.resetPassword.tokenExpired")
            }
        }
        else {
        	resultFactory.failWithMessageAndStatus(NOT_FOUND, "passwordResetService.resetPassword.tokenNotFound", [token])
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    protected Result<PasswordResetToken> generateResetToken(Staff s1) {
    	int resetTokenSize = grailsApplication.config.textup.resetTokenSize
        String tokenString = Helpers.randomAlphanumericString(resetTokenSize)
        //ensure that our generated token is unique
        while (PasswordResetToken.findByToken(tokenString) != null) {
            tokenString = Helpers.randomAlphanumericString(resetTokenSize)
        }
        PasswordResetToken resetToken = new PasswordResetToken(toBeResetId:s1.id, token:tokenString)
        if (resetToken.save()) { resultFactory.success(resetToken) }
        else { resultFactory.failWithValidationErrors(resetToken.errors) }
    }
}
