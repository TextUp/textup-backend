package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.Phone

// This class is nonabstract for testing + max flexibility. HOWEVER this class is not
// intended to be used directly as subclasses implement the key piece of functionality
// that relates the ids to the recipients

@GrailsTypeChecked
@Validateable
class Recipients<T, E> {

    // ids (and therefore recipients) can be empty
    // should initialize actual ArrayLists rather than using `Collections.emptyList()` because we
    // may want to directly add to these arrays. If we used the singleton static method, then
    // it returns an instance of an AbstractList that will throw an error if we try to interact
    // with it
    List<T> ids = []
    List<E> recipients = []
    Phone phone

    static constraints = { // all nullable: false by default
    }

    // Methods
    // -------

    Recipients<T, E> mergeRecipients(Recipients<?, E> toMergeIn) {
        recipients.addAll(toMergeIn.getRecipients())
        this
    }

    // should be overridden in subclasses
    protected List<E> buildRecipientsFromIds(List<T> ids) { Collections.emptyList() }

    // Property access
    // ---------------

    void setIds(List<E> newIds) {
        if (newIds) {
            ids = newIds
            recipients = buildRecipientsFromIds(newIds)
        }
        else {
            ids = []
            recipients = []
        }
    }
}
