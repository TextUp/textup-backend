package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.ToString
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@ToString
class ResultGroup<T> {

	// make this private to use of the API of this abstraction, avoiding the
	// temptation to just use this class as a wrapper for this collection
	private List<Result<T>> successItems = []
	private List<Result<T>> failureItems = []
	private Map<ResultStatus,Integer> successStatusToCount = new HashMap<>()
	private Map<ResultStatus,Integer> failureStatusToCount = new HashMap<>()

	ResultGroup(Collection<Result<? extends T>> manyRes = null) {
		if (manyRes) {
			add(manyRes)
		}
	}

	static <I, O> ResultGroup<O> collect(List<I> objs, Closure<Result<O>> action) {
		ResultGroup<O> resGroup = new ResultGroup<>()
		objs?.each { I obj -> resGroup << action(obj) }
		resGroup
	}

	static <K, V, O> ResultGroup<O> collect(Map<K, V> objs, Closure<Result<O>> action) {
		ResultGroup<O> resGroup = new ResultGroup<>()
		objs?.each { K key, V value -> resGroup << action(key, value) }
		resGroup
	}

	// Methods
	// -------

	ResultGroup<T> leftShift(Result<? extends T> res) { add(res) }

	ResultGroup<T> leftShift(Collection<Result<? extends T>> manyRes) { add(manyRes) }

	ResultGroup<T> leftShift(ResultGroup<? extends T> resGroup) { add(resGroup) }

	ResultGroup<T> add(Result<? extends T> res) { add([res]) }

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

	ResultGroup<T> logFail(String prefix = "", LogLevel level = LogLevel.ERROR) {
		failureItems.each { Result<T> res -> res.logFail(prefix, level) }
		this
	}

	Result<Void> toEmptyResult(boolean allowSomeFailures) {
		ResultUtils.convertGroupToResult(this, Result.void(), allowSomeFailures)
	}

	Result<List<T>> toResult(boolean allowSomeFailures) {
		Result<List<T>> successRes = IOCUtils.resultFactory.success(getPayload(),
			getSuccessStatus() ?: ResultStatus.OK)
		ResultUtils.convertGroupToResult(this, successRes, allowSomeFailures)
	}

	// Properties
	// ----------

	List<Result<T>> getSuccesses() { Collections.unmodifiableList(successItems) }

	List<Result<T>> getFailures() { Collections.unmodifiableList(failureItems) }

	List<T> getPayload() { successItems.collect { Result<T> res -> res.payload } }

	List<String> getErrorMessages() { // TODO test
		List<String> errors = []
		failureItems.each { Result<T> res -> errors.addAll(res.errorMessages) }
		errors
	}

	boolean getIsEmpty() { successItems.isEmpty() && failureItems.isEmpty() }

	boolean getAnySuccesses() { !successItems.isEmpty() }

	boolean getAnyFailures() { !failureItems.isEmpty() }

	ResultStatus getSuccessStatus() { MapUtils.findHighestValue(successStatusToCount)?.key }

	ResultStatus getFailureStatus() { MapUtils.findHighestValue(failureStatusToCount)?.key }

	// Helpers
	// -------

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
}
