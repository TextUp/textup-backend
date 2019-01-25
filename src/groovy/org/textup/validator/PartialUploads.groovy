package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class PartialUploads implements CanValidate {

    private final Collection<Tuple<UploadItem, MediaElement>> tuples

    final Collection<UploadItem> uploads
    final Collection<MediaElement> elements

    static constraints = {
        elements cascadeValidation: true, validator: { Collection<MediaElement> val, PartialUploads obj ->
            if (val?.size() != obj.uploads?.size()) { ["missingInfo"] }
        }
        uploads cascadeValidation: true
    }

    static Result<PartialUploads> tryCreate(Collection<UploadItem> uItems) {
        ResultGroup.collect(uItems) { UploadItem uItem -> createAndAdd(uItem) }
            .toResult(false)
            .then { Collection<Tuple<UploadItem, MediaElement>> tuples ->
                DomainUtils.tryValidate(new PartialUploads(tuples: tuples), ResultStatus.CREATED)
            }
    }

    // Methods
    // -------

    void eachUpload(Closure<?> action) {
        tuples.each { Tuple<UploadItem, MediaElement> tup1 ->
            Tuple.split(tup1) { UploadItem uItem1, MediaElement el1 -> action.call(uItem1, el1) }
        }
    }

    // Properties
    // ----------

    Collection<UploadItem> getUploads() {
        tuples.collect { Tuple<UploadItem, MediaElement> tup1 -> tup1.first }
    }

    Collection<MediaElement> getElements() {
        tuples.collect { Tuple<UploadItem, MediaElement> tup1 -> tup1.second }
    }

    // Helpers
    // -------

    protected Result<Tuple<UploadItem, MediaElement>> createAndAdd(UploadItem uItem) {
        MediaElement.tryCreate(null, [uItem])
            .then { MediaElement el1 -> IOCUtils.resultFactory.success(uItem, el1) }
    }
}
