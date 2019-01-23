package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class MediaInfos {

    static Result<MediaInfo> mustFindForId(Long mId) {
        MediaInfo mInfo = MediaInfo.get(mId)
        if (mInfo) {
            IOCUtils.resultFactory.success(mInfo)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("tryFinishProcessing.mediaInfoNotFound", // TODO
                ResultStatus.NOT_FOUND, [mId])
        }
    }
}
