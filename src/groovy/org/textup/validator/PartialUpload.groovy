package org.textup.validator

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class PartialUploads implements Validateable, Dehydratable<PartialUploads.Dehydrated> {

    final List<UploadItem> uploads = [] // not private for validation + bulk uploading
    private final List<MediaElement> elements = []

    static constraints = {
        mediaElement cascadeValidation: true, validator: { List<MediaElement> val, PartialUploads obj ->
            if (val?.size() != obj.uploads?.size()) { ["missingInfo"] }
        }
        uploadItem cascadeValidation: true
    }

    static class Dehydrated implements Rehydratable<PartialUploads> {
        private final List<Long> elementIds
        private final List<UploadItem> uploads

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
        MediaElement.tryCreate(null, [initialUpload.toMediaElementVersion()])
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
