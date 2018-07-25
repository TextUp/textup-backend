package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import org.textup.*

@GrailsCompileStatic
@Validateable
abstract class Recipients<T, E> {

    // ids (and therefore recipients) can be empty
    protected Collection<T> ids = Collections.emptyList()
    protected Phone phone

    static constraints = { // all nullable: false by default
    }

    void setIds(Collection<T> newIds) { ids = newIds }
    void setPhone(Phone p1) { phone = p1 }

    abstract List<E> getRecipients()

    Recipients<T, E> mergeRecipients(Recipients<?, E> toMergeIn) {
        getRecipients().addAll(toMergeIn.getRecipients())
        this
    }
}
