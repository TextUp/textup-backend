package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class MediaActionService implements HandlesActions<MediaInfo, List<UploadItem>> {

    @Override
    boolean hasActions(Map body) { !!body.doMediaActions }

    @Override
    Result<List<UploadItem>> tryHandleActions(MediaInfo mInfo, Map body) {
        ActionContainer.tryProcess(MediaAction, body.doMediaActions)
            .then { List<MediaAction> actions ->
                ResultGroup<UploadItem> outcomes = new ResultGroup<>()
                actions.each { MediaAction a1 ->
                    switch (a1) {
                        case MediaAction.ADD:
                            outcomes << MediaPostProcessor
                                .buildInitialData(a1.buildType(), a1.buildByteData())
                            break
                        default: // MediaAction.REMOVE
                            mInfo.removeMediaElement(a1.uid)
                    }
                }
                outcomes.toResult(false)
            }
    }
}
