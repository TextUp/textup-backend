package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.media.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class MediaActionService implements HandlesActions<MediaInfo, ResultGroup<UploadItem>> {

    @Override
    boolean hasActions(Map body) { !!body.doMediaActions }

    @Override
    Result<ResultGroup<UploadItem>> tryHandleActions(MediaInfo mInfo, Map body) {
        ActionContainer.tryProcess(MediaAction, body.doMediaActions)
            .then { List<MediaAction> actions ->
                ResultGroup<UploadItem> outcomes = new ResultGroup<>()
                actions.each { MediaAction a1 ->
                    switch (a1) {
                        case MediaAction.ADD:
                            outcomes << MediaPostProcessor.buildInitialData(a1.type, a1.byteData)
                            break
                        default: // MediaAction.REMOVE
                            mInfo.removeMediaElement(a1.uid)
                    }
                }
                IOCUtils.resultFactory.success(outcomes)
            }
    }
}
