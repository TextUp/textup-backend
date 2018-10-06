package org.textup

import com.pusher.rest.data.Result as PusherResult
import com.sendgrid.Response as SendGridResponse
import grails.compiler.GrailsCompileStatic
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.NoSuchMessageException
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError

@GrailsCompileStatic
@Log4j
class ResultFactory {

	@Autowired
	MessageSource messageSource

	// Success
	// -------

    public <X, Y> Result<Tuple<X, Y>> success(X first, Y second,
        ResultStatus status = ResultStatus.OK) {

        Result.createSuccess(new Tuple(first, second), status)
    }
	public <T> Result<T> success(T payload, ResultStatus status = ResultStatus.OK) {
		Result.createSuccess(payload, status)
	}
	Result<Void> success() {
		Result.<Void>createSuccess(null, ResultStatus.NO_CONTENT)
	}

	// Failure
	// -------

    public <T> Result<T> failWithResultsAndStatus(Collection<Result<T>> results, ResultStatus status) {
        List<String> messages = []
    	results.each { Result<?> res -> messages += res.errorMessages }
    	Result.<T>createError(messages, status)
    }

    public <T> Result<T> failWithGroup(ResultGroup<T> resGroup) {
        failWithResultsAndStatus(resGroup.failures, resGroup.failureStatus)
    }

    public <T> Result<T> failWithCodeAndStatus(String code, ResultStatus status, List params = []) {
        Result.<T>createError([getMessage(code, params)], status)
    }

	public <T> Result<T> failWithThrowable(Throwable t) {
        Result.<T>createError([t.message], ResultStatus.INTERNAL_SERVER_ERROR)
	}

    public <T> Result<T> failWithValidationErrors(Errors errors) {
        failWithManyValidationErrors([errors])
    }

    public <T> Result<T> failWithManyValidationErrors(Collection<Errors> manyErrors) {
        List<String> messages = []
    	manyErrors.each { Errors errors ->
    		messages += errors.allErrors.collect { ObjectError e1 -> this.getMessage(e1) }
		}
    	Result.<T>createError(messages, ResultStatus.UNPROCESSABLE_ENTITY)
    }

    // Service-specific failure
    // ------------------------

    public <T> Result<T> failForPusher(PusherResult pRes) {
    	Result.<T>createError([pRes?.message],
            // anecdotally, PusherResult may have null httpStatus when the network connection is spotty
            // and the message will be for a `java.net.UnknownHostException`
            pRes?.httpStatus ? ResultStatus.convert(pRes.httpStatus) : ResultStatus.SERVICE_UNAVAILABLE)
    }

    public <T> Result<T> failForSendGrid(SendGridResponse response) {
    	Result.<T>createError([response.body], ResultStatus.convert(response.statusCode))
    }

    // Helpers
    // -------

    // Wrap getting message in these try catch blocks to avoid losing transaction data
    // due to the uncaught exception being thrown. Instead, we log this error so we can go back
    // in the future to review the logs and correct these problems without disrupting the
    // flow of operations of the overall application
    protected String getMessage(String code, List params) {
    	try {
    		messageSource.getMessage(code, params as Object[], LCH.getLocale())
    	}
    	catch (NoSuchMessageException e) {
    		log.error("ResultFactory.getMessage for code $code with error ${e.message}")
            e.printStackTrace()
    		""
    	}
    }
    protected String getMessage(MessageSourceResolvable resolvable) {
    	try {
    		messageSource.getMessage(resolvable, LCH.getLocale())
    	}
    	catch (NoSuchMessageException e) {
    		log.error("ResultFactory.getMessage for resolvable $resolvable with error ${e.message}")
            e.printStackTrace()
    		""
    	}
    }
}
