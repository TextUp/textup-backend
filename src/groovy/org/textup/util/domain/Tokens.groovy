package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Tokens {

    static Result<Token> mustFindActiveForType(TokenType type, String token) {
        Token tok1 = Token.findByTypeAndToken(type, token)
        if (tok1) {
            if (tok1.isExpired) {
                IOCUtils.resultFactory.failWithCodeAndStatus("tokenService.tokenExpired", // TODO
                    ResultStatus.BAD_REQUEST)
            }
            else { IOCUtils.resultFactory.success(tok1) }
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("tokenService.tokenNotFound", // TODO
                ResultStatus.NOT_FOUND, [token])
        }
    }
}
