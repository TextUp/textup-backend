package org.textup.type

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.springframework.http.HttpStatus

@GrailsTypeChecked
@Log4j
enum ResultStatus {
	ACCEPTED(HttpStatus.ACCEPTED),
	ALREADY_REPORTED(HttpStatus.ALREADY_REPORTED),
	BAD_GATEWAY(HttpStatus.BAD_GATEWAY),
	BAD_REQUEST(HttpStatus.BAD_REQUEST),
	BANDWIDTH_LIMIT_EXCEEDED(HttpStatus.BANDWIDTH_LIMIT_EXCEEDED),
	CHECKPOINT(HttpStatus.CHECKPOINT),
	CONFLICT(HttpStatus.CONFLICT),
	CONTINUE(HttpStatus.CONTINUE),
	CREATED(HttpStatus.CREATED),
	EXPECTATION_FAILED(HttpStatus.EXPECTATION_FAILED),
	FAILED_DEPENDENCY(HttpStatus.FAILED_DEPENDENCY),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	FOUND(HttpStatus.FOUND),
	GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
	GONE(HttpStatus.GONE),
	HTTP_VERSION_NOT_SUPPORTED(HttpStatus.HTTP_VERSION_NOT_SUPPORTED),
	I_AM_A_TEAPOT(HttpStatus.I_AM_A_TEAPOT),
	IM_USED(HttpStatus.IM_USED),
	INSUFFICIENT_STORAGE(HttpStatus.INSUFFICIENT_STORAGE),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
	LENGTH_REQUIRED(HttpStatus.LENGTH_REQUIRED),
	LOCKED(HttpStatus.LOCKED),
	LOOP_DETECTED(HttpStatus.LOOP_DETECTED),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
	MOVED_PERMANENTLY(HttpStatus.MOVED_PERMANENTLY),
	MULTI_STATUS(HttpStatus.MULTI_STATUS),
	MULTIPLE_CHOICES(HttpStatus.MULTIPLE_CHOICES),
	NETWORK_AUTHENTICATION_REQUIRED(HttpStatus.NETWORK_AUTHENTICATION_REQUIRED),
	NO_CONTENT(HttpStatus.NO_CONTENT),
	NON_AUTHORITATIVE_INFORMATION(HttpStatus.NON_AUTHORITATIVE_INFORMATION),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
	NOT_EXTENDED(HttpStatus.NOT_EXTENDED),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED),
	NOT_MODIFIED(HttpStatus.NOT_MODIFIED),
	OK(HttpStatus.OK),
	PARTIAL_CONTENT(HttpStatus.PARTIAL_CONTENT),
	PAYMENT_REQUIRED(HttpStatus.PAYMENT_REQUIRED),
	PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED),
	PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED),
	PROCESSING(HttpStatus.PROCESSING),
	PROXY_AUTHENTICATION_REQUIRED(HttpStatus.PROXY_AUTHENTICATION_REQUIRED),
	REQUEST_HEADER_FIELDS_TOO_LARGE(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE),
	REQUEST_TIMEOUT(HttpStatus.REQUEST_TIMEOUT),
	REQUESTED_RANGE_NOT_SATISFIABLE(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE),
	RESET_CONTENT(HttpStatus.RESET_CONTENT),
	SEE_OTHER(HttpStatus.SEE_OTHER),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
	SWITCHING_PROTOCOLS(HttpStatus.SWITCHING_PROTOCOLS),
	TEMPORARY_REDIRECT(HttpStatus.TEMPORARY_REDIRECT),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	UPGRADE_REQUIRED(HttpStatus.UPGRADE_REQUIRED),
	VARIANT_ALSO_NEGOTIATES(HttpStatus.VARIANT_ALSO_NEGOTIATES)

	private final HttpStatus springStatus
	private final int intStatus

	ResultStatus(HttpStatus stat) {
		this.springStatus = stat
		this.intStatus = stat.value()
	}

	int getIntStatus() { this.intStatus }
	HttpStatus getApiStatus() { this.springStatus }
	// define success as any 1xx, 2xx or 3xx http status code
	boolean getIsSuccess() { this.intStatus < 400 }

	static ResultStatus convert(HttpStatus status) {
		if (!status) {
			return null
		}
		ResultStatus foundStat = ResultStatus.values().find { ResultStatus rStat ->
			rStat.springStatus == status
		}
		if (!foundStat) {
			log.error("convert: could not find an appropriate wrapper for Spring Status $status")
			foundStat = ResultStatus.INTERNAL_SERVER_ERROR
		}
		foundStat
	}

	static ResultStatus convert(int status) {
		ResultStatus foundStat = ResultStatus.values().find { ResultStatus rStat ->
			rStat.intStatus == status
		}
		if (!foundStat) {
			foundStat = ResultStatus.INTERNAL_SERVER_ERROR
		}
		foundStat
	}
}
