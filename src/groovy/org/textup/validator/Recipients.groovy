package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import org.textup.*

@GrailsCompileStatic
@Validateable
abstract class Recipients<T, E> {

    // ids (and therefore recipients) can be empty
    Collection<T> ids = Collections.emptyList()
    Phone phone

    static constraints = { // all nullable: false by default
    }

    abstract List<E> getRecipients()

    Recipients<T, E> merge(Recipients<T, E> toMergeIn) {
        ids.addAll(toMergeIn.ids)
        getRecipients().addAll(toMergeIn.getRecipients())
        this
    }
}
