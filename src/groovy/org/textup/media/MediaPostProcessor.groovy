package org.textup.media

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
class MediaPostProcessor {

    static Result<UploadItem> buildInitialData(MediaType type, byte[] data) {
        getProcessor(type, data).then { CanProcessMedia processor ->
            processor.withCloseable { processor.createInitialVersion() }
        }
    }

    static Result<Tuple<UploadItem, List<UploadItem>>> process(MediaType type, byte[] data) {
        getProcessor(type, data).then { CanProcessMedia processor ->
            processor.withCloseable {
                processor
                    .createSendVersion()
                    .then { UploadItem sendVersion ->
                        ResultGroup<UploadItem> alternates = processor.createAlternateVersions()
                            .logFail("MediaPostProcessor: creating alternate versions")
                        Helpers.resultFactory.success(sendVersion, alternates.successes)
                    }
            }
        }
    }

    // Helpers
    // -------

    protected static Result<CanProcessMedia> getProcessor(MediaType type, byte[] data) {
        ResultFactory resultFactory = Helpers.resultFactory
        if (!type || !data) {
            return resultFactory.failWithCodeAndStatus("mediaPostProcessor.missingInfo",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        // build processor
        if (type in MediaType.IMAGE_TYPES) {
            resultFactory.success(new ImagePostProcessor(type, data))
        }
        else if (type in MediaType.AUDIO_TYPES) {
            resultFactory.success(new AudioPostProcessor(type, data))
        }
        else {
            resultFactory.failWithCodeAndStatus("mediaPostProcessor.unsupportedMediaType",
                ResultStatus.UNPROCESSABLE_ENTITY, [type])
        }
    }
}
