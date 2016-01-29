package org.textup

import groovy.transform.ToString

@ToString
class ResultMap<T,P> {

	Map<T,Result<P>> results = []

	// CRUD
	// ----

	boolean contains(T key) {
		results.containsKey(key)
	}
	boolean isSuccess(T key) {
		results.containsKey(key) ? results[key].success : false
	}
	boolean isFail(T key) {
		results.containsKey(key) ? !results[key].success : false
	}

	Result<P> getAt(T key) {
		results[key]
	}

	Result<P> putAt(T key, Result<P> value) {
		results[key] = value
		value
	}

	Collection<Result<P>> getResults() {
		results.values()
	}

	// Helpers
	// -------

	Map<T,Result<P>> getSuccesses() {
		this.results.findAll { it.success }
	}
	Map<T,Result<P>> getFailures() {
		this.results.findAll { !it.success }
	}

	def any(Closure successAction) {
		Map<T,Result<P>> successes = this.successes
		successes ? successAction(successes) : this
	}
	def any(Closure successAction, Closure failAction) {
		Map<T,Result<P>> successes = this.successes
		successes ? successAction(successes) : failAction(this.failures)
	}

	def every(Closure successAction) {
		Map<T,Result<P>> successes = this.successes
		(successes.size() == this.results.size()) ? successAction(successes) : this
	}

	ResultMap<T,P> logFail(String prefix="") {
		this.results.each { T key, Result<P> value ->
			value.logIfFail("${prefix}: ${key}")
		}
		this
	}
}
