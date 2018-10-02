package org.textup.media

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.EqualsAndHashCode
import java.nio.file.*
import org.textup.*
import org.textup.type.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class AudioPostProcessor implements CanProcessMedia {

    protected static final MediaType SEND_TYPE = MediaType.AUDIO_MP3
    protected static final MediaType ALT_TYPE = MediaType.AUDIO_WEBM_OPUS
    protected static final long CONVERSION_TIMEOUT_IN_MILLIS = 15000

    private final AudioUtils _audioUtils
    private final MediaType _type
    private final Path _temp

    AudioPostProcessor(MediaType t1, byte[] d1) {
        _audioUtils = Holders.applicationContext.getBean(AudioUtils) as AudioUtils
        _type = t1
        _temp = _audioUtils.createTempFile(d1)
    }

    void close() {
        _audioUtils.delete(_temp)
    }

    Result<? extends UploadItem> createSendVersion() {
        convertData(SEND_TYPE)
    }

    ResultGroup<? extends UploadItem> createAlternateVersions() {
        convertData(ALT_TYPE).toGroup()
    }

    // Helpers
    // -------

    Result<UploadItem> convertData(MediaType outputType) {
        if (!_temp) {
            return Helpers.resultFactory.failWithCodeAndStatus("audioPostProcessor.convertData.missingTemp",
                ResultStatus.INTERNAL_SERVER_ERROR, [_type])
        }
        _audioUtils
            .convert(CONVERSION_TIMEOUT_IN_MILLIS, _type, _temp, outputType)
            .then { byte[] newData ->
                UploadItem uItem = new UploadItem(type: outputType, data: newData)
                if (uItem.validate()) {
                    Helpers.resultFactory.success(uItem)
                }
                else { Helpers.resultFactory.failWithValidationErrors(uItem.errors) }
            }
    }
}
