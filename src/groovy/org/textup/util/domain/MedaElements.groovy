package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class MediaElements {

    static Result<MediaElement> create(UploadItem sVersion, Collection<UploadItem> alternates) {
        MediaElement e1 = new MediaElement(sendVersion: sVersion?.toMediaElementVersion())
        alternates?.each { UploadItem uItem ->
            e1.addToAlternateVersions(uItem.toMediaElementVersion())
        }
        Utils.trySave(e1)
    }
}
