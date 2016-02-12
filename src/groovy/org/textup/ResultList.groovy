package org.textup

import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@ToString
class ResultList<T> {

	List<Result<T>> results = []

	// Constructors
	// ------------

	ResultList() {
		this.results = []
	}

	ResultList(Result<T> res) {
		this.results << res
	}

	ResultList(List<Result<T>> listOfRes) {
		this.results += listOfRes
	}

	ResultList(ResultList<T> resList) {
		this.results += resList.results
	}

	// Add results
	// -----------

	ResultList<T> plus(Result<T> res) {
		this.results << res
		this
	}

	ResultList<T> plus(ResultList<T> resList) {
		this.results += resList.results
		this
	}

	ResultList<T> leftShift(Result<T> res) {
		this.results << res
		this
	}

	ResultList<T> leftShift(ResultList<T> resList) {
		this.results += resList.results
		this
	}

	// Helpers
	// -------

	boolean getIsAnySuccess() {
		this.successes.size() > 0
	}
	boolean getIsAllSuccess() {
		this.successes.size() == this.results.size()
	}

	Collection<Result<T>> getSuccesses() {
		this.results.findAll { it.success }
	}
	Collection<Result<T>> getFailures() {
		this.results.findAll { !it.success }
	}

	def any(Closure successAction) {
		Collection<Result<T>> successes = this.successes
		successes ? successAction(successes) : this
	}
	def any(Closure successAction, Closure failAction) {
		Collection<Result<T>> successes = this.successes
		successes ? successAction(successes) : failAction(this.failures)
	}

	def every(Closure successAction) {
		Collection<Result<T>> successes = this.successes
		(successes.size() == this.results.size()) ? successAction(successes) : this
	}

	ResultList<T> logFail(String prefix="") {
		this.results.each { Result<T> res -> res.logFail(prefix) }
		this
	}
}
