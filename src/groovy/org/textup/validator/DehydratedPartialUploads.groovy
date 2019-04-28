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
@TupleConstructor(includeFields = true)
@Validateable
class DehydratedPartialUploads implements CanValidate, Rehydratable<PartialUploads> {

    private final Collection<Tuple<UploadItem, Long>> tuples

    static Result<DehydratedPartialUploads> tryCreate(PartialUploads pu1) {
        DomainUtils.tryValidate(pu1).then {
            Collection<Tuple<UploadItem, Long>> tuples = []
            pu1.eachUpload { UploadItem uItem1, MediaElement el1 ->
                tuples << Tuple.create(uItem1, el1.id)
            }
            DehydratedPartialUploads dpu1 = new DehydratedPartialUploads(tuples)
            DomainUtils.tryValidate(dpu1, ResultStatus.CREATED)
        }
    }

    // Methods
    // -------

    @Override
    Result<PartialUploads> tryRehydrate() {
        Collection<MediaElement> els = AsyncUtils.getAllIds(MediaElement, tuples*.second as Collection<Long>)
        Map<Long, MediaElement> idToEl = MapUtils.buildObjectMap(els) { MediaElement el1 -> el1.id }
        Collection<Tuple<UploadItem, MediaElement>> hydratedTuples = []
        tuples.each { Tuple<UploadItem, Long> tup1 ->
            Tuple.split(tup1) { UploadItem uItem1, Long elId ->
                if (idToEl.containsKey(elId)) {
                    hydratedTuples << Tuple.create(uItem1, idToEl[elId])
                }
                else { log.error("tryRehydrate: media element `${elId}` was not found") }
            }
        }
        PartialUploads.tryCreateFromTuples(hydratedTuples)
    }
}
