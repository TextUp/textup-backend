package org.textup.validator

import groovy.transform.ToString
import org.groovy.enum.AuthorType

@ToString
class Author {
	Long id
    String name
    AuthorType type
}
