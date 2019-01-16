package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class MediaInfos {

    static Result<MediaInfo> mustFindForId(Long mId) {
        MediaInfo mInfo = MediaInfo.get(mId)
        if (mInfo) {
            IOCUtils.resultFactory.success(mInfo)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("tryFinishProcessing.mediaInfoNotFound", // TODO
                ResultStatus.NOT_FOUND, [mediaId])
        }
    }
}
