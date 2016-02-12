package org.textup

import org.springframework.validation.Errors
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import grails.compiler.GrailsCompileStatic
import org.textup.types.ResultType

@GrailsCompileStatic
@Log4j
class ResultFactory {

	@Autowired
	MessageSource messageSource

	/////////////
	// Success //
	/////////////

	Result success(payload) {
		new Result(success:true, payload:payload, type:ResultType.SUCCESS)
	}
	Result<?> success() {
		new Result<?>(success:true, payload:null, type:ResultType.SUCCESS)
	}

	/////////////
	// Failure //
	/////////////

	Result failWithMessage(String messageCode, List params=[]) {
		String message = messageSource.getMessage(messageCode, params as Object[], LCH.getLocale())
		new Result(success:false, payload:[code:messageCode, message:message],
            type:ResultType.MESSAGE)
	}
	Result failWithMessageAndStatus(HttpStatus status, String messageCode, List params=[]) {
		String message = messageSource.getMessage(messageCode, params as Object[], LCH.getLocale())
		new Result(success:false, payload:[code:messageCode, message:message, status:status],
            type:ResultType.MESSAGE_STATUS)
	}
    Result failWithMessagesAndStatus(HttpStatus status, Collection<String> messages) {
        new Result(success:false, payload:[status:status, messages:messages],
            type:ResultType.MESSAGE_LIST_STATUS)
    }
    Result failWithResultsAndStatus(HttpStatus status, Collection<Result> results) {
        Collection<String> messages = []
        results.each { Result res ->
            if (!res.success) { messages += res.errorMessages }
        }
        new Result(success:false, payload:[status:status, messages:messages],
            type:ResultType.MESSAGE_LIST_STATUS)
    }
	Result failWithThrowable(Throwable t) {
		new Result(success:false, payload:t, type:ResultType.THROWABLE)
	}
    Result failWithValidationErrors(Errors verrors) {
    	new Result(success:false, payload:verrors,
            type:ResultType.VALIDATION)
    }
}
