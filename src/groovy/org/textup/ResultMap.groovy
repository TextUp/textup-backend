package org.textup

import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@ToString
class ResultMap<P> {

	Map<String,Result<P>> results = [:]

	// CRUD
	// ----

	boolean contains(String key) {
		results.containsKey(key)
	}
	boolean isSuccess(String key) {
		results.containsKey(key) ? results[key].success : false
	}
	boolean isFail(String key) {
		results.containsKey(key) ? !results[key].success : false
	}

	Result<P> getAt(String key) {
		results[key]
	}

	Result<P> putAt(String key, Result<P> value) {
		results[key] = value
		value
	}

	Collection<Result<P>> getResults() {
		results.values()
	}

	// Helpers
	// -------

	Map<String,Result<P>> getSuccesses() {
		this.results.findAll { it.value.success }
	}
	Map<String,Result<P>> getFailures() {
		this.results.findAll { !it.value.success }
	}

	def any(Closure successAction) {
		Map<String,Result<P>> successes = this.successes
		successes ? successAction(successes) : this
	}
	def any(Closure successAction, Closure failAction) {
		Map<String,Result<P>> successes = this.successes
		successes ? successAction(successes) : failAction(this.failures)
	}

	def every(Closure successAction) {
		Map<String,Result<P>> successes = this.successes
		(successes.size() == this.results.size()) ? successAction(successes) : this
	}

	ResultMap<P> logFail(String prefix="") {
		this.results.each { String key, Result<P> value ->
			value.logFail("${prefix}: ${key}")
		}
		this
	}
}
