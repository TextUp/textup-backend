package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class MediaElements {

    static Result<MediaElement> create(UploadItem sVersion, Collection<UploadItem> alternates) {
        MediaElement e1 = new MediaElement(sendVersion: sVersion?.toMediaElementVersion())
        alternates?.each { UploadItem uItem ->
            e1.addToAlternateVersions(uItem.toMediaElementVersion())
        }

        if (e1.save()) {
            IOCUtils.resultFactory.success(e1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(e1.errors) }
    }
}
