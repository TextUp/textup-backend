package org.textup.media

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.transform.EqualsAndHashCode
import java.nio.file.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class AudioPostProcessor implements CanProcessMedia {

    protected static final MediaType SEND_TYPE = MediaType.AUDIO_MP3
    protected static final MediaType ALT_TYPE = MediaType.AUDIO_WEBM_OPUS
    protected static final long CONVERSION_TIMEOUT_IN_MILLIS = 60000

    private final AudioUtils _audioUtils
    private final MediaType _type
    private final byte[] _data
    private final Path _temp

    AudioPostProcessor(MediaType t1, byte[] d1) {
        _audioUtils = Holders.applicationContext.getBean(AudioUtils) as AudioUtils
        _type = t1
        _data = d1
        _temp = _audioUtils.createTempFile(d1)
    }

    @Override
    void close() {
        _audioUtils.delete(_temp)
    }

    @Override
    Result<UploadItem> createInitialVersion() {
        UploadItem.tryCreate(_type, _data)
    }

    @Override
    Result<UploadItem> createSendVersion() {
        convertData(SEND_TYPE)
    }

    @Override
    ResultGroup<UploadItem> createAlternateVersions() {
        convertData(ALT_TYPE).toGroup()
    }

    // Helpers
    // -------

    protected Result<UploadItem> convertData(MediaType outputType) {
        if (!_temp) {
            return IOCUtils.resultFactory.failWithCodeAndStatus("audioPostProcessor.missingTempFile",
                ResultStatus.INTERNAL_SERVER_ERROR, [_type])
        }
        _audioUtils
            .convert(CONVERSION_TIMEOUT_IN_MILLIS, _type, _temp, outputType)
            .then { byte[] newData -> UploadItem.tryCreate(outputType, newData) }
    }
}
