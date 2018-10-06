package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.RollbackOnResultFailure

@GrailsTypeChecked
@Transactional
class PasswordResetService {

    MailService mailService
    ResultFactory resultFactory
    TokenService tokenService

    @RollbackOnResultFailure
    Result<Void> start(String username) {
        Staff s1 = Staff.findByUsername(username?.trim()?.toLowerCase())
        if (!s1) {
            return resultFactory.failWithCodeAndStatus("passwordResetService.start.staffNotFound",
                ResultStatus.NOT_FOUND, [username])
        }
        else if (!s1.email) {
            return resultFactory.failWithCodeAndStatus("passwordResetService.start.staffNoEmail",
                ResultStatus.NOT_FOUND)
        }
        tokenService
            .generatePasswordReset(s1.id)
            .then { Token tok1 -> mailService.notifyPasswordReset(s1, tok1.token) }
    }

    @RollbackOnResultFailure
    Result<Staff> finish(String token, String password) {
        tokenService
            .findPasswordResetStaff(token)
            .then { Staff s1 ->
                s1.password = password
                if (s1.save()) {
                    resultFactory.success(s1)
                }
                else { resultFactory.failWithValidationErrors(s1.errors) }
            }
    }
}
