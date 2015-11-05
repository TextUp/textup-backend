package org.textup

import grails.validation.ValidationErrors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus

class ResultFactory {

	@Autowired
	MessageSource messageSource

	/////////////
	// Success //
	/////////////

	Result success(payload) {
		new Result(success:true, payload:payload, type:Constants.RESULT_SUCCESS)
	}
	Result<?> success() {
		new Result<?>(success:true, payload:null, type:Constants.RESULT_SUCCESS)	
	}

	/////////////
	// Failure //
	/////////////

	Result<Map> failWithMessage(String messageCode, List params=[]) {
		String message = messageSource.getMessage(messageCode, params as Object[], LCH.getLocale())
		new Result<Map>(success:false, payload:[code:messageCode, message:message], type:Constants.RESULT_MESSAGE)
	}
	Result<Map> failWithMessageAndStatus(HttpStatus status, String messageCode, List params=[]) {
		String message = messageSource.getMessage(messageCode, params as Object[], LCH.getLocale())
		new Result<Map>(success:false, payload:[code:messageCode, message:message, status:status], type:Constants.RESULT_MESSAGE_STATUS)
	}
	Result<Throwable> failWithThrowable(Throwable t) {
		new Result<Throwable>(success:false, payload:t, type:Constants.RESULT_THROWABLE)
	}
    Result<ValidationErrors> failWithValidationErrors(ValidationErrors verrors) {
    	new Result<ValidationErrors>(success:false, payload:verrors, type:Constants.RESULT_VALIDATION)
    }

    //////////////////
    // RecordResult //
    //////////////////

    Result<RecordResult> successWithRecordResult(RecordItem item) {
    	RecordResult recResult = new RecordResult(newItems:[item])
        this.success(recResult)
    }
    Result convertToRecordResult(Result res) {
    	if (res.success && res.payload?.instanceOf(RecordItem)) {
            RecordResult recResult = new RecordResult(newItems:[res.payload])
            res = this.success(recResult)
        }
        res
    }
}