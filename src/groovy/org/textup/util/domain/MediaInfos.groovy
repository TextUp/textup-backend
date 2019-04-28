package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
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
            IOCUtils.resultFactory.failWithCodeAndStatus("mediaInfos.notFound",
                ResultStatus.NOT_FOUND, [mId])
        }
    }
}
