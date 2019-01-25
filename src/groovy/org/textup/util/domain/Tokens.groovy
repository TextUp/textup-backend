package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class Tokens {

    static Result<Token> mustFindActiveForType(TokenType type, String token) {
        Token tok1 = Token.findByTypeAndToken(type, token)
        if (tok1) {
            if (tok1.isExpired) {
                IOCUtils.resultFactory.failWithCodeAndStatus("tokens.expired",
                    ResultStatus.BAD_REQUEST, [tok1.id])
            }
            else { IOCUtils.resultFactory.success(tok1) }
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("tokens.notFound",
                ResultStatus.NOT_FOUND, [token])
        }
    }
}
