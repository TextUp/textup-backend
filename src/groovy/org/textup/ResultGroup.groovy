package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.ToString
import org.textup.type.LogLevel

@GrailsCompileStatic
@ToString
class ResultGroup<T> {

	// make this private to use of the API of this abstraction, avoiding the
	// temptation to just use this class as a wrapper for this collection
	private List<Result<T>> successItems = []
	private List<Result<T>> failureItems = []
	private Map<ResultStatus,Integer> successStatusToCount = new HashMap<>()
	private Map<ResultStatus,Integer> failureStatusToCount = new HashMap<>()

	// Constructors
	// ------------

	ResultGroup() {}
	ResultGroup(Collection<Result<? extends T>> manyRes) {
		add(manyRes)
	}

	// Add items
	// ---------

	ResultGroup<T> leftShift(Result<? extends T> res) {
		add(res)
	}
	ResultGroup<T> leftShift(Collection<Result<? extends T>> manyRes) {
		add(manyRes)
	}
	ResultGroup<T> leftShift(ResultGroup<? extends T> resGroup) {
		add(resGroup)
	}
	ResultGroup<T> add(Result<? extends T> res) {
		add([res])
	}
	ResultGroup<T> add(Collection<Result<? extends T>> manyRes) {
		manyRes?.each { Result<? extends T> res ->
			if (res.success) {
				addHelper(res, successItems, successStatusToCount)
			}
			else { addHelper(res, failureItems, failureStatusToCount) }
		}
		this
	}
	ResultGroup<T> add(ResultGroup<? extends T> resGroup) {
		add(resGroup.successes)
		add(resGroup.failures)
	}
	protected void addHelper(Result<? extends T> res, List<Result<T>> items,
		Map<ResultStatus,Integer> statusToCount) {
		// we used to use HashSets to ensure uniqueness of the results in this group
		// however, enforcing uniqueness sometimes results in unintended behavior
		// (for example, if we are adding two results that have payloads whose equals
		// implementations happen to cause them to equal then the group will not
		// add the second result). Therefore, we decided to have the group
		// simply accept all items added, even duplicate items
		items.add(res)
		if (statusToCount.containsKey(res.status)) {
			// do not use "+= 1" because of a Groovy compilation bug
			// https://issues.apache.org/jira/browse/GROOVY-7110
			statusToCount[res.status] = statusToCount[res.status] + 1
		}
		else { statusToCount[res.status] = 1 }
	}

	// Groupings
	// ---------

	List<Result<T>> getSuccesses() {
		Collections.unmodifiableList(this.successItems)
	}
	List<Result<T>> getFailures() {
		Collections.unmodifiableList(this.failureItems)
	}
	List<T> getPayload() {
		this.successItems.collect { Result<T> res -> res.payload }
	}

	// Status
	// ------

	boolean getIsEmpty() {
		this.successItems.isEmpty() && this.failureItems.isEmpty()
	}
	boolean getAnySuccesses() {
		!this.successItems.isEmpty()
	}
	boolean getAnyFailures() {
		!this.failureItems.isEmpty()
	}

	ResultStatus getSuccessStatus() {
		Helpers.findHighestValue(successStatusToCount)?.key
	}
	ResultStatus getFailureStatus() {
		Helpers.findHighestValue(failureStatusToCount)?.key
	}

	ResultGroup<T> logFail(String prefix = "", LogLevel level = LogLevel.ERROR) {
		this.failureItems.each { Result<T> res -> res.logFail(prefix, level) }
		this
	}
}
