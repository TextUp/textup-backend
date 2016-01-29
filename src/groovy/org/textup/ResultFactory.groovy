package org.textup

import grails.validation.ValidationErrors
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus

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

	Result<Map> failWithMessage(String messageCode, List params=[]) {
		String message = messageSource.getMessage(messageCode, params as Object[], LCH.getLocale())
		new Result<Map>(success:false, payload:[code:messageCode, message:message],
            type:ResultType.MESSAGE)
	}
	Result<Map> failWithMessageAndStatus(HttpStatus status, String messageCode, List params=[]) {
		String message = messageSource.getMessage(messageCode, params as Object[], LCH.getLocale())
		new Result<Map>(success:false, payload:[code:messageCode, message:message, status:status],
            type:ResultType.MESSAGE_STATUS)
	}
    Result<Map> failWithMessagesAndStatus(HttpStatus status, Collection<String> messages) {
        new Result<Map>(success:false, payload:[status:status, messages:messages],
            type:ResultType.MESSAGE_LIST_STATUS)
    }
    Result<Map> failWithResultsAndStatus(HttpStatus status, Collection<Result> results) {
        Collection<String> messages = []
        results.each { Result res ->
            if (!res.success) { messages += res.errorMessages }
        }
        new Result<Map>(success:false, payload:[status:status, messages:messages],
            type:ResultType.MESSAGE_LIST_STATUS)
    }
	Result<Throwable> failWithThrowable(Throwable t) {
		new Result<Throwable>(success:false, payload:t, type:ResultType.THROWABLE)
	}
    Result<ValidationErrors> failWithValidationErrors(ValidationErrors verrors) {
    	new Result<ValidationErrors>(success:false, payload:verrors,
            type:ResultType.VALIDATION)
    }
}
