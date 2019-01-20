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
        Staffs.mustFindForUsername(username)
            .then { Staff s1 -> tokenService.generatePasswordReset(s1.id).curry(s1) }
            .then { Staff s1, Token tok1 -> mailService.notifyPasswordReset(s1, tok1) }
    }

    @RollbackOnResultFailure
    Result<Staff> finish(String token, String password) {
        tokenService.findPasswordResetStaff(token)
            .then { Staff s1 ->
                s1.password = password
                DomainUtils.trySave(s1)
            }
    }
}
