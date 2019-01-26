package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@ToString
@TupleConstructor(includeFields = true)
@Validateable
class Author implements CanValidate {

	final Long id
    final String name
    final AuthorType type

    static Author create(IncomingSession is1) {
        Author.create(is1.id, is1.number.prettyPhoneNumber, AuthorType.SESSION)
    }

    static Author create(Staff s1) {
        Author.create(s1.id, s1.name, AuthorType.STAFF)
    }

    static Author create(Long id, String name, AuthorType type) { new Author(id, name, type) }
}
