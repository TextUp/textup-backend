package org.textup

import groovy.transform.ToString

@ToString
class RecordResult {
	Set<RecordItem> newItems = new HashSet<>()
	Set<Long> invalidOrForbiddenContactableIds = new HashSet<>()
	Set<Long> invalidOrForbiddenTagIds = new HashSet<>()
	Set<String> invalidNumbers = new HashSet<>()
	Collection<String> errorMessages = []

	RecordResult merge(RecordResult recRes) {
		this.with {
			newItems += recRes.newItems
			invalidOrForbiddenContactableIds += recRes.invalidOrForbiddenContactableIds
			invalidOrForbiddenTagIds += recRes.invalidOrForbiddenTagIds
			invalidNumbers += recRes.invalidNumbers
			errorMessages += recRes.errorMessages
		}
		this
	}
}
