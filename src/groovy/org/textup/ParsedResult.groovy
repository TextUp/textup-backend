package org.textup

import groovy.transform.ToString

@ToString
class ParsedResult<E,I> {
	List<E> valid = new ArrayList<>()
	List<I> invalid = new ArrayList<>()
}