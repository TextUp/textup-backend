package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true, excludes = ["elements", "uploads"])
@Validateable
class PartialUploads implements CanValidate {

    private final Collection<Tuple<UploadItem, MediaElement>> tuples

    final Collection<MediaElement> elements
    final Collection<UploadItem> uploads

    static constraints = {
        elements cascadeValidation: true, validator: { Collection<MediaElement> val, PartialUploads obj ->
            if (val?.size() != obj.uploads?.size()) {
                ["missingInfo", val*.id]
            }
        }
        uploads cascadeValidation: true
    }

    static Result<PartialUploads> tryCreate(Collection<UploadItem> uItems) {
        ResultGroup
            .collect(uItems) { UploadItem uItem ->
                MediaElement.tryCreate([uItem]).then { MediaElement el1 ->
                    IOCUtils.resultFactory.success(uItem, el1)
                }
            }
            .toResult(false)
            .then { Collection<Tuple<UploadItem, MediaElement>> tuples ->
                PartialUploads.tryCreateFromTuples(tuples)
            }
    }

    static Result<PartialUploads> tryCreateFromTuples(Collection<Tuple<UploadItem, MediaElement>> tuples) {
        DomainUtils.tryValidate(new PartialUploads(tuples), ResultStatus.CREATED)
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
}
