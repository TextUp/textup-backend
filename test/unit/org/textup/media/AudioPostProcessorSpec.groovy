package org.textup.media

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class AudioPostProcessorSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
        audioUtils(AudioUtils,
            TestHelpers.config.textup.media.audio.executableDirectory,
            TestHelpers.config.textup.media.audio.executableName,
            TestHelpers.config.textup.tempDirectory)
    }

    def cleanup() {
        TestHelpers.clearTempDirectory()
    }

    void "test closing audio post processor triggers appropriate cleanup"() {
        given:
        int numTempFiles = TestHelpers.numInTempDirectory

        when:
        AudioPostProcessor processor = new AudioPostProcessor(null, null)

        then:
        TestHelpers.numInTempDirectory == numTempFiles + 1

        when:
        processor.close()

        then:
        TestHelpers.numInTempDirectory == numTempFiles
    }

    @Unroll
    void "test creating initial version for #type"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(type)
        AudioPostProcessor processor = new AudioPostProcessor(type, data)

        when:
        Result<UploadItem> res = processor.createInitialVersion()

        then:
        res.status == ResultStatus.OK
        res.payload instanceof UploadItem
        res.payload.type == type
        res.payload.data == data

        where:
        type                        | _
        MediaType.AUDIO_MP3         | _
        MediaType.AUDIO_OGG_VORBIS  | _
        MediaType.AUDIO_OGG_OPUS    | _
        MediaType.AUDIO_WEBM_VORBIS | _
        MediaType.AUDIO_WEBM_OPUS   | _
    }

    @Unroll
    void "test creating send version for #type"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(type)
        AudioPostProcessor processor = new AudioPostProcessor(type, data)

        when: "pass in content type and data"
        Result<UploadItem> res = processor.createSendVersion()

        then: "create send version with appropriate width and file size"
        res.status == ResultStatus.OK
        res.payload instanceof UploadItem
        res.payload.type == AudioPostProcessor.SEND_TYPE
        res.payload.widthInPixels == null
        res.payload.heightInPixels == null
        res.payload.sizeInBytes > 0
        res.payload.isPublic == false

        where:
        type                        | _
        MediaType.AUDIO_MP3         | _
        MediaType.AUDIO_OGG_VORBIS  | _
        MediaType.AUDIO_OGG_OPUS    | _
        MediaType.AUDIO_WEBM_VORBIS | _
        MediaType.AUDIO_WEBM_OPUS   | _
    }

    @Unroll
    void "test creating display versions for #type"() {
        given:
        byte[] data = TestHelpers.getSampleDataForMimeType(type)
        AudioPostProcessor processor = new AudioPostProcessor(type, data)

        when:
        ResultGroup<UploadItem> resGroup = processor.createAlternateVersions()

        then:
        resGroup.successStatus == ResultStatus.OK
        resGroup.successes.size() == 1
        resGroup.payload[0].type == AudioPostProcessor.ALT_TYPE
        resGroup.payload[0].widthInPixels == null
        resGroup.payload[0].heightInPixels == null
        resGroup.payload[0].sizeInBytes > 0
        resGroup.payload[0].isPublic == false

        where:
        type                        | _
        MediaType.AUDIO_MP3         | _
        MediaType.AUDIO_OGG_VORBIS  | _
        MediaType.AUDIO_OGG_OPUS    | _
        MediaType.AUDIO_WEBM_VORBIS | _
        MediaType.AUDIO_WEBM_OPUS   | _
    }
}
