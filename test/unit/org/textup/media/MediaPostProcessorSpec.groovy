package org.textup.media

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class MediaPostProcessorSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
        audioUtils(AudioUtils,
            TestUtils.config.textup.media.audio.executableDirectory,
            TestUtils.config.textup.media.audio.executableName,
            TestUtils.config.textup.tempDirectory)
    }

    void "test getting processor given invalid type"() {
        given:
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }

        when: "missing data"
        Result<CanProcessMedia> res = MediaPostProcessor.getProcessor(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "mediaPostProcessor.missingInfo"

        when: "unsupport media type"
        res = MediaPostProcessor.getProcessor(MediaType.IMAGE_UNKNOWN, "hi".bytes)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "mediaPostProcessor.unsupportedMediaType"
    }

    @Unroll
    void "test getting processor for #type"() {
        when:
        Result<CanProcessMedia> res = MediaPostProcessor.getProcessor(type, "hi".bytes)

        then:
        res.status == ResultStatus.OK
        res.payload.class == processorClass

        where:
        type                        | processorClass
        MediaType.IMAGE_PNG         | ImagePostProcessor
        MediaType.IMAGE_JPEG        | ImagePostProcessor
        MediaType.IMAGE_GIF         | ImagePostProcessor
        MediaType.AUDIO_MP3         | AudioPostProcessor
        MediaType.AUDIO_OGG_VORBIS  | AudioPostProcessor
        MediaType.AUDIO_OGG_OPUS    | AudioPostProcessor
        MediaType.AUDIO_WEBM_VORBIS | AudioPostProcessor
        MediaType.AUDIO_WEBM_OPUS   | AudioPostProcessor
    }

    @Unroll
    void "test building initial versions for #type"() {
        given:
        byte[] data = TestUtils.getSampleDataForMimeType(type)
        int numTempFiles = TestUtils.numInTempDirectory

        when:
        Result<UploadItem> res = MediaPostProcessor.buildInitialData(type, data)

        then:
        TestUtils.numInTempDirectory == numTempFiles
        res.status == ResultStatus.OK
        res.payload.type == type
        res.payload.data == data

        where:
        type                        | _
        MediaType.IMAGE_PNG         | _
        MediaType.IMAGE_JPEG        | _
        MediaType.IMAGE_GIF         | _
        MediaType.AUDIO_MP3         | _
        MediaType.AUDIO_OGG_VORBIS  | _
        MediaType.AUDIO_OGG_OPUS    | _
        MediaType.AUDIO_WEBM_VORBIS | _
        MediaType.AUDIO_WEBM_OPUS   | _
    }

    @Unroll
    void "test creating versions overall for image of #type"() {
        given:
        byte[] data = TestUtils.getSampleDataForMimeType(type)
        int numTempFiles = TestUtils.numInTempDirectory

        when: "pass in data"
        Result<Tuple<UploadItem, List<UploadItem>>> res = MediaPostProcessor.process(type, data)

        then: "get back both send and alternate versions"
        TestUtils.numInTempDirectory == numTempFiles
        res.status == ResultStatus.OK
        res.payload.first.type == type
        res.payload.first.sizeInBytes < data.length
        res.payload.second.size() == 3
        res.payload.second.every { it.type == type }

        where:
        type                        | _
        MediaType.IMAGE_PNG         | _
        MediaType.IMAGE_JPEG        | _
        MediaType.IMAGE_GIF         | _
    }

    @Unroll
    void "test creating versions overall for audio of #type"() {
        given:
        byte[] data = TestUtils.getSampleDataForMimeType(type)
        int numTempFiles = TestUtils.numInTempDirectory

        when: "pass in data"
        Result<Tuple<UploadItem, List<UploadItem>>> res = MediaPostProcessor.process(type, data)

        then: "get back both send and alternate versions"
        TestUtils.numInTempDirectory == numTempFiles
        res.payload.first.type == AudioPostProcessor.SEND_TYPE
        res.payload.second.size() == 1
        res.payload.second.every { it.type == AudioPostProcessor.ALT_TYPE }

        where:
        type                        | _
        MediaType.AUDIO_MP3         | _
        MediaType.AUDIO_OGG_VORBIS  | _
        MediaType.AUDIO_OGG_OPUS    | _
        MediaType.AUDIO_WEBM_VORBIS | _
        MediaType.AUDIO_WEBM_OPUS   | _
    }
}
