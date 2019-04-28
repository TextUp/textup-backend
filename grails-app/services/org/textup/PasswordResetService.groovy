package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class PasswordResetService {

    MailService mailService
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
