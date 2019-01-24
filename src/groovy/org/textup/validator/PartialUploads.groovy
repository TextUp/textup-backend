package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked // TODO
@Validateable
class PartialUploads implements CanValidate, Dehydratable<PartialUploads.Dehydrated> {

    final Collection<UploadItem> uploads = [] // not private for validation + bulk uploading
    private final Collection<MediaElement> elements = []

    static constraints = {
        mediaElement cascadeValidation: true, validator: { Collection<MediaElement> val, PartialUploads obj ->
            if (val?.size() != obj.uploads?.size()) { ["missingInfo"] }
        }
        uploadItem cascadeValidation: true
    }

    static class Dehydrated implements Rehydratable<PartialUploads> {
        private final Collection<Long> elementIds
        private final Collection<UploadItem> uploads

        @Override
        Result<PartialUploads> tryRehydrate() {
            PartialUploads pu1 = new PartialUploads(uploads: uploads,
                elements: AsyncUtils.getAllIds(MediaElement, elementIds))
            DomainUtils.tryValidate(pu1)
        }
    }

    // Methods
    // -------

    @Override
    PartialUploads.Dehydrated dehydrate() {
        new PartialUploads.Dehydrated(elementIds: elements*.id, uploads: uploads)
    }

    Result<MediaElement> createAndAdd(UploadItem uItem) {
        MediaElement.tryCreate(null, [uItem])
            .then { MediaElement el1 ->
                elements << el1
                uploads << uItem
                DomainUtils.trySave(el1)
            }
    }

    void eachUpload(Closure<?> action) {
        int num = elements.size()
        if (num == uploads.size()) {
            for (int i = 0; i < num; ++i) {
                action.call(uploads[i], elements[i])
            }
        }
    }
}
